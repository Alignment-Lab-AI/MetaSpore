//
// Copyright 2022 DMetaSoul
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.dmetasoul.metaspore.recommend.controll;

import com.dmetasoul.metaspore.recommend.common.Utils;
import com.dmetasoul.metaspore.recommend.configure.TaskFlowConfig;
import com.dmetasoul.metaspore.recommend.data.DataContext;
import com.dmetasoul.metaspore.recommend.data.DataResult;
import com.dmetasoul.metaspore.recommend.data.ServiceResult;
import com.dmetasoul.metaspore.recommend.TaskServiceRegister;
import com.dmetasoul.metaspore.recommend.dataservice.DataService;
import com.google.common.collect.Maps;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.json.JsonObject;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * 用于实现restfull接口
 * Created by @author qinyy907 in 14:24 22/07/15.
 */
@Slf4j
@RestController
@RequestMapping("/service")
public class ServiceController {

    @Autowired
    public TaskFlowConfig taskFlowConfig;

    @Autowired
    public TaskServiceRegister taskServiceRegister;

    /**
     * 用于实现restfull接口 /service/get/{task}
     * @param task 需要调用的任务名称，需要在配置中事先定义好，不可为空
     * @param req 需要传递给任务task的请求参数
     * @return ServiceResult 包含状态码，错误信息以及数据结果
     * Created by @author qinyy907 in 14:24 22/07/15.
     */
    @RequestMapping(value = "/get/{task}", method = POST, produces = "application/json")
    public ServiceResult getTaskData(@PathVariable String task, @RequestBody Map<String, Object> req) {
        log.info("get task :{}, request:{}", task, req);
        log.info("test services: {}", taskServiceRegister.getDataServices().get("twoTower_user4"));

        DataService taskService = taskServiceRegister.getTaskService(task);
        if (taskService == null) {
            return ServiceResult.of(-1, "taskService is not exist!");
        }
        DataResult result = taskService.execute(new DataContext(req));
        if (result == null) {
            return ServiceResult.of(-1, "taskService execute fail!");
        }
        if (!result.isVaild()) {
            return ServiceResult.of(-1, "taskService result is invalid!");
        }
        return ServiceResult.of(result);
    }
    /**
     * 用于实现restfull接口 /service/recommend/{scene}/{id}
     * req 设置debug字段输出各个阶段的输出结果
     * @param scene 需要推荐的场景名称，需要在配置中事先定义好，不可为空
     * @param id 需要被推荐用户的id，比如user id，不可为空
     * @param req 需要传递给推荐任务的请求参数，可为空
     * @return ServiceResult 包含状态码，错误信息以及数据结果
     * Created by @author qinyy907 in 14:24 22/07/15.
     */
    @RequestMapping(value = "/recommend/{scene}/{id}", method = POST, produces = "application/json")
    public ServiceResult recommend(@PathVariable String scene, @PathVariable String id, @RequestBody Map<String, Object> req) {
        DataService taskService = taskServiceRegister.getTaskService(scene);
        if (taskService == null) {
            return ServiceResult.of(-1, String.format("scene:%s is not support!", scene));
        }
        DataContext context = new DataContext(req);
        if (StringUtils.isEmpty(id)) {
            return ServiceResult.of(-1, String.format("scene:%s recommend need id, eg:userId!", scene));
        }
        context.setId(id);
        DataResult result = taskService.execute(context);
        if (result == null) {
            return ServiceResult.of(-1, "scene execute fail!");
        }
        if (!result.isVaild()) {
            return ServiceResult.of(-1, "scene result is invalid!");
        }
        if (Utils.getField(req, "debug", false)) {
            Map<String, Object> values = Maps.newHashMap();
            values.putAll(context.getResults());
            result = new DataResult();
            result.setValues(values);
        }
        return ServiceResult.of(result, id);
    }

    /**
     * for debug configure use
     * @param name config name
     * @return config content
     */
    @RequestMapping(value = "/config/{name}", method = GET, produces = "application/json")
    public ServiceResult getConfig(@PathVariable String name) {
        log.info("get config :{}", name);
        Map<String, Object> values = Maps.newHashMap();
        values.put(name, null);
        if (taskFlowConfig.getServices() != null && taskFlowConfig.getServices().containsKey(name)) {
            values.put(name, taskFlowConfig.getServices().get(name));
        }
        if (taskFlowConfig.getChains() != null && taskFlowConfig.getChains().containsKey(name)) {
            values.put(name, taskFlowConfig.getChains().get(name));
        }
        if (taskFlowConfig.getAlgoTransforms() != null && taskFlowConfig.getAlgoTransforms().containsKey(name)) {
            values.put(name, taskFlowConfig.getAlgoTransforms().get(name));
        }
        if (taskFlowConfig.getFeatures() != null && taskFlowConfig.getFeatures().containsKey(name)) {
            values.put(name, taskFlowConfig.getFeatures().get(name));
        }
        if (taskFlowConfig.getSourceTables() != null && taskFlowConfig.getSourceTables().containsKey(name)) {
            values.put(name, taskFlowConfig.getSourceTables().get(name));
        }
        if (taskFlowConfig.getLayers() != null && taskFlowConfig.getLayers().containsKey(name)) {
            values.put(name, taskFlowConfig.getLayers().get(name));
        }
        if (taskFlowConfig.getScenes() != null && taskFlowConfig.getScenes().containsKey(name)) {
            values.put(name, taskFlowConfig.getScenes().get(name));
        }
        if (taskFlowConfig.getExperiments() != null && taskFlowConfig.getExperiments().containsKey(name)) {
            values.put(name, taskFlowConfig.getExperiments().get(name));
        }
        if (taskFlowConfig.getSources() != null && taskFlowConfig.getSources().containsKey(name)) {
            values.put(name, taskFlowConfig.getSources().get(name));
        }
        DataResult result = new DataResult();
        result.setValues(values);
        return ServiceResult.of(result);
    }
}
