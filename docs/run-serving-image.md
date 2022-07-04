# 1. Brief Introduction
Metaspore serving service is a model inference service implemented in c++. It supports the DNN model generated by MetaSpore training, and also supports online inference of various models such as xgboost, lightgbm, sklearn, huggingface, etc. The serving service provides an interface for grpc remote call, which can be called by Java, Python and other languages as clients.

# 2.  Save model to local
Take xgboost as an example, train the model and save it in onnx format:
```python
import xgboost as xgb
import numpy as np
import pathlib
import os

data = np.random.rand(5, 10).astype('f')  # 5 entities, each contains 10 features
label = np.random.randint(2, size=5)  # binary target
dtrain = xgb.DMatrix(data, label=label)

param = {'max_depth': 2, 'eta': 1, 'objective': 'binary:logistic'}
param['nthread'] = 4
param['eval_metric'] = 'auc'

num_round = 10
bst = xgb.train(param, dtrain, num_round, )

from onnxmltools import convert_xgboost
from onnxconverter_common.data_types import FloatTensorType

initial_types = [('input', FloatTensorType(shape=[-1, 10]))]
xgboost_onnx_model = convert_xgboost(bst, initial_types=initial_types, target_opset=14)

output_dir = "output/model_export/xgboost_model/"

pathlib.Path(output_dir).mkdir(parents=True, exist_ok=True)
pathlib.Path(os.path.join(output_dir, 'dense')).mkdir(parents=True, exist_ok=True)

with open(os.path.join(output_dir, 'dense/model.onnx'), "wb") as f:
    f.write(xgboost_onnx_model.SerializeToString())
with open(os.path.join(output_dir, 'dense_schema.txt'), "w") as f:
    f.write('table: input\n')
```
# 3. Start metaspore serving service through docker image
Start the docker container and put the model directory ${pwd}/output/model on the host. Exported model dir is mount to container /data/models. Note that the host directory is generated in the previous step and should pass the parent directory of the model directory.

```bash
docker run -d --name=test-serving --net host -v ${PWD}/output/model_export:/data/models dmetasoul/metaspore-serving-release:cpu-v1.0.1 /opt/metaspore-serving/bin/metaspore-serving-bin -grpc_listen_port 50000 -init_load_path /data/models
```

  * **Note**: if GPU is required for prediction, the docker image is:
    ```
    dmetasoul/metaspore-serving-release:gpu-v1.0.1
    ```
    
    When starting the service, you need to add the parameter `--gpus all` after `docker run`. NVIDIA docker plugin needs to be installed in advance on the host.

After starting, you can check whether the model is loaded successfully:
```bash
docker logs test-serving
```
Log output contains the following line:

TabularModel loaded from /data/models/xgboost, required inputs [input], producing outputs [label, probabilities]

It indicates that the xgboost model is loaded successfully.

The log contains `use cuda:0`, indicating that the service recognizes the GPU and automatically uses the GPU for prediction.

# 4. Call the serving service
## 4.1 Python
Installation dependency:
```bash
pip install grpcio-tools pyarrow
```
Generate grpc Python definition file:
```bash
# Metaspore needs to be replaced by metaspore code directory
python -m grpc_tools.protoc -I MetaSpore/protos/ --python_out=. --grpc_python_out . MetaSpore/protos/metaspore.proto
```

Call the serving service example Python code:
```python
import grpc

import metaspore_pb2
import metaspore_pb2_grpc

import pyarrow as pa

with grpc.insecure_channel('0.0.0.0:50000') as channel:
    stub = metaspore_pb2_grpc.PredictStub(channel)
    row = []
    values = [0.6558618,0.13005558,0.03510657,0.23048967,0.63329154,0.43201634,0.5795548,0.5384891,0.9612295,0.39274803]
    for i in range(10):
        row.append(pa.array([values[i]], type=pa.float32()))
    rb = pa.RecordBatch.from_arrays(row, [f'field_{i}' for i in range(10)])
    sink = pa.BufferOutputStream()
    with pa.ipc.new_file(sink, rb.schema) as writer:
        writer.write_batch(rb)
    payload_map = {"input": sink.getvalue().to_pybytes()}
    request = metaspore_pb2.PredictRequest(model_name="xgboost_model", payload=payload_map)
    reply = stub.Predict(request)
    for name in reply.payload:
        with pa.BufferReader(reply.payload[name]) as reader:
            tensor = pa.ipc.read_tensor(reader)
            print(f'Tensor: {tensor.to_numpy()}')
```
## 4.2 Java
Can be executed locally https://github.com/meta-soul/MetaSpore/blob/main/java/online-serving/serving/src/test/java/com/dmetasoul/metaspore/serving/DenseXGBoostTest.java This test calls the xgboost model prediction.

## 5.  Production deployment for serving service
We provide k8s helm chart:
https://github.com/meta-soul/MetaSpore/tree/main/kubernetes/serving-chart