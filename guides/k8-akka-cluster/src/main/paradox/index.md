# How to setup Akka cluster to Kubernetes

## The problem

You've created an application using [Akka](http://akka.io/) with [Akka Clustering](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) enabled. After evaluating all of your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: how do you setup an Akka cluster on Kubernetes? Finally, running an application on Kubernetes requires containerization. How exactly do we deploy these systems to Kubernetes?

## The solution

This guide will cover the steps required to deploy a simple Akka based application to Kubernetes.

_If you are deploying Lagom or Play based application, please refer to [Deploying Microservices to Kubernetes](http://todo-link) as it covers how to deploy both Lagom and Play based application to Kubernetes._

As part of Kubernetes deployment, application must be containerized. As such, this guide will show how to containerize the application using [SBT](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/).

This guide will also show how to configure Kubernetes resources, particularly [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to establish Akka cluster for your application.

### Prerequisites

* An existing, running Kubernetes cluster.
* The kubernetes CLI tool `kubectl` is configured to point to the existing Kubernetes cluster.
* Docker environment variables are configured to point to Docker registry used by Kubernetes cluster. This will ensure the Docker images we built in this guide will be available to the Kubernetes cluster.
* JDK8+
* An existing Akka based application which uses either SBT or Maven as the build tool that you'd like to deploy to Kubernetes.


### Solutions overview

First, we will need to containerize our application and publish it to the Docker registry used by our Kubernetes cluster. This guide will show how to configure both SBT and Maven to perform this task.

Once our image is published, we will utilize Kubernetes [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to deploy application. Using StatefulSet, given an a service named `myapp` and `3` replicas, Kubernetes will start `3` instances of [Pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/) with the names `myapp-0`, `myapp-1`, and `myapp-2`. These Pod names will be registered in the Kubernetes DNS, such that they can be resolved by the pods within the same StatefulSet. This would mean `myapp-0` as a host name can be resolved within the `myapp-2` pod, for example.

Based on the above example, the container `myapp-0` can be used as the seed node to form the Akka cluster within Kubernetes.

Each Pod will also need to expose the Akka remoting port so it is accessible from a different Pod instances. We will utilize Kubernetes [Service](https://kubernetes.io/docs/concepts/services-networking/service/) to achieve this.


### Naming your application

In this guide we will refer to the application name as `myapp` - please feel free to substitute the application name with the actual name of the application you are going to deploy.

We'd like to bring your attention to the application name as they would be used to define the service name in the Kubernetes StatefulSet and Kubernetes Service.

The application name will also be referenced in the SBT or Maven build file, and as such the application name will form the Docker image name when containerized.



--- The lines below here is still not written nicely, skeleton sections only.



### System properties

Environment variable defined by StatefulSet will be used as part of `java` command line argument to setup Akka cluster.

For example, we will be adding the following system properties as part of our application start up.

```
-DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME}
-Dakka.actor.provider=cluster
-Dakka.remote.netty.tcp.hostname="$AKKA_REMOTING_BIND_HOST"
-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" -Dakka.cluster.seed-nodes.0="akka.tcp://${AKKA_ACTOR_SYSTEM_NAME}@${AKKA_SEED_NODE_HOST}:${AKKA_SEED_NODE_PORT}"
```

<todo table of environment variable & purpose>

Application will setup the `ActorSystem` name based on the system property `actorSystemName`.


### Publishing to Docker registry - SBT

Enable [SBT Native Packager](http://www.scala-sbt.org/sbt-native-packager/).

#### Multi-module project

#### Simple project

#### Notes on SBT build (make title nicer)

<todo>

### Publishing to Docker registry - Maven

Add Maven [fabric8](https://dmp.fabric8.io/) docker plugin.

#### Multi-module project

For a multi-module Maven project, register the fabric8 docker plugin on the project's `pom.xml`.

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

For each module you wish to containerize, enable the fabric8 docker plugin.

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

#### Simple project

For a simple project with single `pom.xml`, enable the fabric8 docker plugin as such.

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

#### Notes on Maven build (make title nicer)

* Base image from `openjdk:8-jre-alpine`.
* Image is tagged with `latest` and `${project.version}`.
* Note `<entryPoint>` has additional system property required to setup Akka cluster declared. The value of each of these system properties are derived from the environment variable to be supplied by the Kubernetes StatefulSet.


### Creating Kubernetes Service for Akka remoting

<todo intro>

<todo how to pipe this into kubectl>

```
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

```

* Metadata `name` has the value of `myapp-akka-remoting` - this is the name of the service.
* Metadata `labels` has `app` having the value of `myapp` - this is to allow query service based on `myapp`.
* The `spec/selector/app` has the value of `myapp`.
  * Means that all pods created by the `StatefulSet` that has `label` `app=myapp` will have `TCP` port `2551` accessible from other container. This is required for the Akka remoting connectivity between pods so cluster can be established.

### Creating Kubernetes StatefulSet resource

<todo>


## Conclusion

<todo putting it all together>
