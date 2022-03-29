# MovieLens Recommend Online Service

## 1. Install online-serving components
You need to maven install [online-serving](https://github.com/meta-soul/MetaSpore/tree/main/java/online-serving) components 
before launching our online recommend service.
1. **serving** is used to do model inference.
2. **feature-extract** is a maven plugin which generate domains and repositories of MongoDB using its [table yaml files](https://github.com/meta-soul/MetaSpore/tree/main/demo/movielens/online/src/main/resources/tables). [More information](https://github.com/meta-soul/MetaSpore/blob/main/java/online-serving/feature-extract/README.md)
3. **experiment-pipeline** is an experiment framework which help us do A/B testing more easily. [More information](https://github.com/meta-soul/MetaSpore/blob/main/java/online-serving/experiment-pipeline/README.md).
```
cd MetaSpore/java/online-serving
mvn clean install 
```
Then use feature-extract to generate MongoDB related files:
```
cd MetaSpore/demo/movielens/online
mvn com.dmetasoul:feature-extract:1.0-SNAPSHOT:generate
```
Finally, mark directory(target/generated-sources/feature/java) as "Generated Sources Root" if you use IntelliJ IDEA.

## 2. Create application-dev.properties
You need to create resources/**application-dev.properties** from [application-template.properties](https://github.com/meta-soul/MetaSpore/blob/main/demo/movielens/online/src/main/resources/application-template.properties) and specify:
1. MongoDB related configurations. 
2. NPS related configurations.
3. Milvus related configurations.
4. (Just ignore mysql configurations. We don't use it in this demo but we support multi data sources.)

## 3. Install and configure Consul
You could modify Consul's Key/Value pair to dynamically change the online A/B testing strategies.
1. [Install Consul](https://www.consul.io/downloads) and launch it.
2. Visit Consul's [portal](http://localhost:8500/ui/dc1/kv), and create a new Key/Value pair:
   1. Key is `config/test/scene-config`
   2. Copy this [YAML content](https://github.com/meta-soul/MetaSpore/blob/main/demo/movielens/online/src/main/resources/experiment.yaml) as value. 
   3. The config file of Consul of our demo is [here](https://github.com/meta-soul/MetaSpore/blob/main/demo/movielens/online/src/main/resources/bootstrap.yml).

## 4. Launch recommend online service
You could run online service entry point (MovielensRecommendApplication.java) and test it now.
For example: `curl http://localhost:8080/user/10` to get recommended movies for user with id 10.
