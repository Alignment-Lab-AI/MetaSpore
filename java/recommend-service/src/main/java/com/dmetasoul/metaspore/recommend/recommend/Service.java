package com.dmetasoul.metaspore.recommend.recommend;

import com.dmetasoul.metaspore.recommend.TaskServiceRegister;
import com.dmetasoul.metaspore.recommend.annotation.ServiceAnnotation;
import com.dmetasoul.metaspore.recommend.common.DataTypes;
import com.dmetasoul.metaspore.recommend.common.Utils;
import com.dmetasoul.metaspore.recommend.configure.RecommendConfig;
import com.dmetasoul.metaspore.recommend.configure.TaskFlowConfig;
import com.dmetasoul.metaspore.recommend.data.DataContext;
import com.dmetasoul.metaspore.recommend.data.DataResult;
import com.dmetasoul.metaspore.recommend.dataservice.AlgoTransformTask;
import com.dmetasoul.metaspore.recommend.dataservice.DataService;
import com.dmetasoul.metaspore.recommend.enums.DataTypeEnum;
import com.dmetasoul.metaspore.serving.ArrowAllocator;
import com.dmetasoul.metaspore.serving.FeatureTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Data
@ServiceAnnotation
public class Service implements BaseService {
    protected String name;
    protected ExecutorService taskPool;
    protected TaskFlowConfig taskFlowConfig;
    protected List<AlgoTransformTask> tasks;
    protected TaskServiceRegister serviceRegister;
    protected RecommendConfig.Service serviceConfig;
    protected long timeout = 30000L;
    protected TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    protected List<Field> resFields;
    protected List<DataTypeEnum> dataTypes;
    protected Map<String, TransformFunction> transformFunctions;
    protected boolean isDup = false;

    public boolean init(String name, TaskFlowConfig taskFlowConfig, TaskServiceRegister serviceRegister) {
        if (StringUtils.isEmpty(name)) {
            log.error("name is null, init fail!");
            return false;
        }
        this.name = name;
        this.taskFlowConfig = taskFlowConfig;
        this.serviceRegister = serviceRegister;
        this.serviceConfig = taskFlowConfig.getServices().get(name);
        this.taskPool = serviceRegister.getTaskPool();
        if (CollectionUtils.isNotEmpty(serviceConfig.getColumnNames())) {
            resFields = Lists.newArrayList();
            dataTypes = Lists.newArrayList();
            for (String col : serviceConfig.getColumnNames()) {
                String type = serviceConfig.getColumnMap().get(col);
                DataTypeEnum dataType = DataTypes.getDataType(type);
                resFields.add(new Field(col, dataType.getType(), dataType.getChildFields()));
                dataTypes.add(dataType);
            }
        }
        this.transformFunctions = Maps.newHashMap();
        initFunctions();
        return initService();
    }

    protected boolean initService() {
        isDup = Utils.getField(serviceConfig.getOptions(), "dupOnMerge", false);
        for (String item : serviceConfig.getTasks()) {
            DataService task = serviceRegister.getDataService(item);
            if (task instanceof AlgoTransformTask) {
                tasks.add((AlgoTransformTask) task);
            } else {
                return false;
            }
            timeout = Utils.getField(serviceConfig.getOptions(), "timeout", timeout);
        }
        return true;
    }

    public String getFieldType(String key) {
        return serviceConfig.getColumnMap().get(key);
    }

    public void addFunctions() {}

    public void initFunctions() {
        addFunction("summary", (data, context, option) -> {
            Assert.notNull(resFields, "summary need configure columns info!");
            FeatureTable featureTable = new FeatureTable(name, resFields, ArrowAllocator.getAllocator());
            DataResult result = new DataResult();
            result.setFeatureTable(featureTable);
            result.setName(name);
            result.mergeDataResult(data, serviceConfig.getColumnMap(), Utils.getField(option, "dupOnMerge", isDup));
            data.clear();
            data.add(result);
            return true;
        });
    }

    public void addFunction(String name, TransformFunction function) {
        transformFunctions.put(name, function);
    }

    @SneakyThrows
    protected CompletableFuture<List<DataResult>> executeTask(List<DataResult> data, DataContext context) {
        List<CompletableFuture<DataResult>> taskList = Lists.newArrayList();
        for (AlgoTransformTask task : tasks) {
            for (DataResult item : data) {
                if (StringUtils.isNotEmpty(item.getName())) {
                    task.setDataResultByName(item.getName(), item, context);
                }
            }
            taskList.add(CompletableFuture.supplyAsync(() -> task.execute(context), taskPool)
                    .whenComplete(((dataResult, throwable) -> {
                        if (throwable != null) {
                            log.error("exception:{}", throwable.getMessage());
                        }
                    }))
            );
        }
        return CompletableFuture.allOf(taskList.toArray(new CompletableFuture[]{}))
                .thenApplyAsync(x -> {
                    List<DataResult> result = Lists.newArrayList();
                    for (CompletableFuture<DataResult> future : taskList) {
                        try {
                            result.add(future.get(timeout, timeUnit));
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return result;
                }, taskPool);
    }

    public CompletableFuture<List<DataResult>> executeTransform(CompletableFuture<List<DataResult>> future,
                                                                List<Map<String, Map<String, Object>>> transforms,
                                                                DataContext context) {
        if (future == null || CollectionUtils.isEmpty(transforms)) return null;
        for (Map<String, Map<String, Object>> functionConfig : transforms) {
            for (Map.Entry<String, Map<String, Object>> item : functionConfig.entrySet()) {
                TransformFunction function = transformFunctions.get(item.getKey());
                if (function == null) {
                    log.error("the service：{} function: {} is not exist!", name, item.getKey());
                    continue;
                }
                Map<String, Object> option = Maps.newHashMap();
                if (MapUtils.isNotEmpty(serviceConfig.getOptions())) {
                    option.putAll(serviceConfig.getOptions());
                }
                if (MapUtils.isNotEmpty(item.getValue())) {
                    option.putAll(item.getValue());
                }
                future = future.thenApplyAsync(dataResults -> {
                    if (CollectionUtils.isEmpty(dataResults)) {
                        throw new RuntimeException("feature result is empty!");
                    }
                    if (!function.transform(dataResults, context, option)) {
                        log.error("the service：{} function: {} execute fail!", name, item.getKey());
                    }
                    return dataResults;
                }, taskPool);
            }
        }
        return future;
    }

    @Override
    @SneakyThrows
    public CompletableFuture<List<DataResult>> execute(List<DataResult> data, DataContext context) {
        CompletableFuture<List<DataResult>> future = CompletableFuture.supplyAsync(() -> data);
        future = executeTransform(future, serviceConfig.getPreTransforms(), context);
        Assert.notNull(future, "Service execute pre-transform function fail at " + name);
        future.thenApplyAsync(dataResults -> {
            if (dataResults == null) dataResults = Lists.newArrayList();
            try {
                dataResults.addAll(executeTask(data, context).get(timeout, timeUnit));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
            return dataResults;
        });
        future = executeTransform(future, serviceConfig.getTransforms(), context);
        Assert.notNull(future, "Service execute transform function fail at " + name);
        return future;
    }

    @Override
    public CompletableFuture<List<DataResult>> execute(DataResult data, DataContext context) {
        return execute(List.of(data), context);
    }

    @Override
    public CompletableFuture<List<DataResult>> execute(DataContext context) {
        Assert.isTrue(CollectionUtils.isNotEmpty(serviceConfig.getTasks()), "executeTask tasks must not empty");
        return execute(List.of(), context);
    }

    @Override
    public void close() {
    }
}
