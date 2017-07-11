# How to setup Akka cluster to Kubernetes

## The problem

You've created an application using [Akka](http://akka.io/) with [Akka Clustering](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) enabled. After evaluating all of your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: how do you setup an Akka cluster on Kubernetes? Finally, running an application on Kubernetes requires containerization. How exactly do we deploy these systems to Kubernetes?

## The solution

This guide will cover the steps required to deploy a simple Akka based application to Kubernetes.

_If you are deploying Lagom or Play based application, please refer to [Deploying Microservices to Kubernetes](http://todo-link) as it covers how to deploy both Lagom and Play based application to Kubernetes._

As part of Kubernetes deployment, application must be containerized. As such, this guide will show how to containerize the application using [SBT](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/).

This guide will also show how to configure Kubernetes resources, particularly [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to establish Akka cluster for your application.

### Prerequisites

* JDK8+
* [Docker](https://www.docker.com/).
* Either [SBT](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/)
* An existing, running Kubernetes cluster.
* The kubernetes CLI tool `kubectl` is installed and configured to point to the existing Kubernetes cluster.
* Docker environment variables are configured to point to Docker registry used by Kubernetes cluster. This will ensure the Docker images we built in this guide will be available to the Kubernetes cluster.
* An existing Akka based application which uses either SBT or Maven as the build tool that you'd like to deploy to Kubernetes.

### Solution overview

First, we will need to containerize the application and publish it to the Docker registry used by the Kubernetes cluster. This guide will show how to configure both SBT and Maven to perform this task.

Once our image is published, we will utilize Kubernetes [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to deploy application. Using StatefulSet, given an a service named `myapp` and `3` replicas, Kubernetes will start `3` instances of [Pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/) with the names `myapp-0`, `myapp-1`, and `myapp-2`. These Pod names will be registered in the Kubernetes DNS, such that they can be resolved by the pods within the same StatefulSet. This would mean `myapp-0` as a host name can be resolved within the `myapp-2` pod, for example.

Based on the above example, the container `myapp-0` can be used as the seed node to form the Akka cluster within Kubernetes.

Each Pod will also need to expose the Akka remoting port so it is accessible from a different Pod instances. We will utilize Kubernetes [Service](https://kubernetes.io/docs/concepts/services-networking/service/) to achieve this.


### Naming your application

In this guide we will refer to the application name as `myapp` - please feel free to substitute the application name with the actual name of the application you are going to deploy.

We'd like to bring your attention to the application name as they would be used to define the service name in the Kubernetes StatefulSet and Kubernetes Service.

The application name will also be referenced in the SBT or Maven build file, and as such the application name will form the Docker image name when containerized.


### System properties

To establish Akka cluster within the Kubernetes container, we will be adding the following system properties when containerizing your application.

```
-Dakka.actor.provider=cluster
-Dakka.remote.netty.tcp.hostname="$AKKA_REMOTING_BIND_HOST"
-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" -Dakka.cluster.seed-nodes.0="akka.tcp://${AKKA_ACTOR_SYSTEM_NAME}@${AKKA_SEED_NODE_HOST}:${AKKA_SEED_NODE_PORT}"
-DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME}
```

Some of the system properties values are derived from environment variables, i.e. `AKKA_SEED_NODE_HOST`. These environment variables will be supplied by the Kubernetes StatefulSet.

| Environment Variable    | Description                         | Example Value |
|-------------------------|-------------------------------------|---------------|
| AKKA_REMOTING_BIND_HOST | The hostname of the Kubernetes Pod. | `myapp-2`     |
| AKKA_REMOTING_BIND_PORT | The Akka remoting port.             | 2551          |
| AKKA_SEED_NODE_HOST     | The hostname of the first container in the StatefulSet. | `myapp-0` |
| AKKA_SEED_NODE_PORT     | The Akka remoting port of the seed node. In most cases this value should match `AKKA_REMOTING_BIND_PORT`. | 2551 |
| AKKA_ACTOR_SYSTEM_NAME  | The name of the `ActorSystem` of your application. For the purpose of this guide, we'll match the `ActorSystem` name with the application name. | 'myapp' |


Either one of the following code can be used by your application to set up the `ActorSystem` name.

```
val actorSystem = sys.props.get("actorSystemName")
  .fold(throw new IllegalArgumentException("Actor system name must be defined"))(ActorSystem(_))
```

Or alternatively, if you have a custom configuration.

```
import com.typesafe.config.ConfigFactory

val actorSystem = sys.props.get("actorSystemName")
  .fold(throw new IllegalArgumentException("Actor system name must be defined")) { actorSystemName =>
    val config = ConfigFactory.load("custom.conf")
    val actorSystem = ActorSystem(actorSystemName, config)
  }
```

--- TODO: SBT Docker Publish

### Publishing to Docker registry - SBT

Enable [SBT Native Packager](http://www.scala-sbt.org/sbt-native-packager/).

#### Multi-module project

#### Simple project

#### Notes on SBT build (make title nicer)

<todo>

--- TODO: SBT Docker Publish

### Publishing to Docker registry - Maven

We will be using [fabric8](https://dmp.fabric8.io/) Maven plugin to containerize the application.


#### Multi-module project

Follow this section if your application is a part of a multi-module Maven project.

Add the following plugin settings on the `pom.xml` under project root directory to register the fabric8 Maven plugin.

```
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.20.1</version>
    <configuration>
        <skip>true</skip>
        <images>
            <image>
                <name>%g/%a:%l</name>
                <build>
                    <from>openjdk:8-jre-alpine</from>
                    <tags>
                        <tag>latest</tag>
                        <tag>${project.version}</tag>
                    </tags>
                    <assembly>
                        <descriptorRef>artifact-with-dependencies</descriptorRef>
                    </assembly>
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

When building a container image for the application, base image from [openjdk:8-jre-alpine](https://hub.docker.com/_/openjdk/) will be used to provide a working JRE 8 installation for the application to run on. The container image name expression is `%g/%a:%l` where `%g`, `%a`, and `%l` are Maven project's group id, artefact id, and the image tag. When published, our image will tagged with `latest` and `${project.version}`

Add the following plugin settings on the `pom.xml` under the application's module directory.

```
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <configuration>
        <skip>false</skip>
        <images>
            <image>
                <build>
                    <entryPoint>
                        java -cp '/maven/*' -DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME} -Dakka.actor.provider=cluster -Dakka.remote.netty.tcp.hostname="$AKKA_REMOTING_BIND_HOST" -Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" -Dakka.cluster.seed-nodes.0="akka.tcp://${AKKA_ACTOR_SYSTEM_NAME}@${AKKA_SEED_NODE_HOST}:${AKKA_SEED_NODE_PORT}" com.mycompany.MainClass
                    </entryPoint>
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

The fabric8 Maven plugin will place all the jar files under `/maven` directory which need to be added to the JVM's classpath. Also note that we have added the Akka clustering related system properties as part of the JVM options.

Replace `com.mycompany.MainClass` with the actual main class of your application.

Execute the following command to containerize and publish your application's Docker image.

```bash
mvn clean package docker:build
```


#### Simple project

Follow this section if your application is built using a simple Maven project, i.e. single `pom.xml` at located at the root of the project's directory.

Add the following plugin settings on the `pom.xml` under project root directory to register the fabric8 Maven plugin.

```
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.20.1</version>
    <configuration>
        <skip>false</skip>
        <images>
            <image>
                <name>%g/%a:%l</name>
                <build>
                    <from>openjdk:8-jre-alpine</from>
                    <tags>
                        <tag>latest</tag>
                        <tag>${project.version}</tag>
                    </tags>
                    <assembly>
                        <descriptorRef>artifact-with-dependencies</descriptorRef>
                    </assembly>
                    <entryPoint>
                        java -cp '/maven/*' -DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME} -Dakka.actor.provider=cluster -Dakka.remote.netty.tcp.hostname="$AKKA_REMOTING_BIND_HOST" -Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" -Dakka.cluster.seed-nodes.0="akka.tcp://${AKKA_ACTOR_SYSTEM_NAME}@${AKKA_SEED_NODE_HOST}:${AKKA_SEED_NODE_PORT}" com.mycompany.MainClass
                    </entryPoint>                    
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

When building a container image for the application, base image from [openjdk:8-jre-alpine](https://hub.docker.com/_/openjdk/) will be used to provide a working JRE 8 installation for the application to run on. The container image name expression is `%g/%a:%l` where `%g`, `%a`, and `%l` are Maven project's group id, artefact id, and the image tag. When published, our image will tagged with `latest` and `${project.version}`

The fabric8 Maven plugin will place all the jar files under `/maven` directory which need to be added to the JVM's classpath. Also note that we have added the Akka clustering related system properties as part of the JVM options.

Replace `com.mycompany.MainClass` with the actual main class of your application.

Execute the following command to containerize and publish your application's Docker image.

```bash
mvn clean package docker:build
```

### Creating Kubernetes Service for Akka remoting

Next we will declare the Akka remoting port we'd like to expose from our Pod using Kubernetes Service so it can be referenced from one Pod to another.

Execute the following command to create Kubernetes Service for the application's Akka remoting port.

```bash
cat << EOF | kubectl create -f -
{
  "apiVersion": "v1",
  "kind": "Service",
  "metadata": {
    "labels": {
      "app": "myapp"
    },
    "name": "myapp-akka-remoting"
  },
  "spec": {
    "clusterIP": "None",
    "ports": [
      {
        "port": 2551,
        "protocol": "TCP",
        "targetPort": 2551
      }
    ],
    "selector": {
      "app": "myapp"
    }
  }
}
EOF
```

This Kubernetes Service has `myapp-akka-remoting` as its name. The service exposes TCP port `2551` which is the application's Akka remoting port.

The service will be applied to the Pods based on the `selector` value. All Pods which has a label called `app` with the value of `myapp` will have the TCP port `2551` exposed.

View the list of Kubernetes Service using the following command.

```bash
kubectl get services
```

The service has a label called `app` with the value of `myapp`, which will allow filtering list of Service based on this label.

```bash
kubectl get services --selector=app=myapp
```

### Creating Kubernetes StatefulSet resource

Once we have the application containerized and published, and the Kubernetes Service declared for the application's Akka remoting port, we are ready to deploy the application.

Execute the following command to deploy the application.

```bash
cat << EOF | kubectl create -f -
{
  "apiVersion": "apps/v1beta1",
  "kind": "StatefulSet",
  "metadata": {
    "name": "myapp"
  },
  "spec": {
    "serviceName": "myapp",
    "replicas": 1,
    "template": {
      "metadata": {
        "labels": {
          "app": "myapp"
        }
      },
      "spec": {
        "containers": [
          {
            "name": "myapp",
            "image": "mygroup/myapp",
            "imagePullPolicy": "Never",
            "ports": [
              {
                "containerPort": 2551,
                "name": "akka-remote"
              }
            ],
            "resources": {
              "limits": {
                "cpu": "250m",
                "memory": "384Mi"
              },
              "requests": {
                "cpu": "250m",
                "memory": "384Mi"
              }
            },
            "env": [
              {
                "name": "AKKA_ACTOR_SYSTEM_NAME",
                "value": "myapp-actor-system"
              },
              {
                "name": "AKKA_REMOTING_BIND_PORT",
                "value": "2551"
              },
              {
                "name": "AKKA_REMOTING_BIND_HOST",
                "valueFrom": {
                  "fieldRef": {
                    "fieldPath": "metadata.name"
                  }
                }
              },
              {
                "name": "AKKA_SEED_NODE_PORT",
                "value": "2551"
              },
              {
                "name": "AKKA_SEED_NODE_HOST",
                "value": "myapp-0"
              }
            ],
            "readinessProbe": {
              "tcpSocket": {
                "port": 2551
              },
              "initialDelaySeconds": 30,
              "timeoutSeconds": 30
            }
          }
        ]
      }
    }
  }
}
EOF
```

The StatefulSet for the application has `myapp` as the name as declared by `spec.serviceName`.

The StatefulSet has `1` replica. You may adjust this number to the number of instances you desire. Alternatively, you can deploy the with `1` replica to begin with, and once the first service is started you may adjust this number by modifying the StatefulSet using `kubectl`.

The Pods for the StatefulSet will be initialized using `mygroup/myapp` as the image. Adjust this image name to match the actual image published by the SBT or Maven build. The `imagePullPolicy` is set to `Never` to ensure only image published to the Docker registry is used. If the image doesn't exist in the Docker registry, the StatefulSet creation will fail with an error.

Each Pod in the StatefulSet will expose port `2551` as declared by the `containerPort` named `akka-remote`.

Please adjust the CPU and memory resources to match the requirements of the application.

The `env` property of the StatefulSet contains the list of environment variable to be passed into the application when it starts. These environment variables matches the environment variables required to populate the system property required by the application. Note that environment variable `AKKA_SEED_NODE_HOST` is set to `myapp-0`, which means that `myapp-0` is used as the seed node to establish the Akka cluster.

We will be using port `2551` to inform Kubernetes that the application is ready as configured by the `readinessProbe`. Depending on the nature of your application, the application might not be ready when the `ActorSystem` starts up. You may opt to inform Kubernetes using a different means of [readinessProbe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/).

Once the StatefulSet is created, you can view the created pods using the following command.

```bash
kubectl get pods --selector=app=myapp
```

Use the following command to view the log messages from a particular pod.

```bash
kubectl logs -f <pod name>
```


## Conclusion

<todo putting it all together>