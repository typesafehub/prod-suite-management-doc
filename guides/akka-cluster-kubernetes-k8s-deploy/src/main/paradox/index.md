# Deploying clustered Akka applications on Kubernetes

[Akka Cluster](http://doc.akka.io/docs/akka/2.5.3/scala/common/cluster.html) is a fault-tolerant peer-to-peer
cluster membership service. [Kubernetes](https://kubernetes.io/), an open-source solution for container orchestration,
provides several features that are a great fit for running applications built with Akka Cluster. This guide will
cover how you can take your Akka Cluster application and configure it to run ontop of Kubernetes, taking advantage of
its many standard features.

## The challenge

Deploying an Akka cluster on Kubernetes presents the following challenges:

* One or more seed notes must be specified to bootstrap the cluster.
* Kubernetes requires apps to be containerized before it will run them.
* Kubernetes requires configuration for stateful applications.

We will show how to containerize the application using [sbt](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/) and how to configure Kubernetes resources, particularly [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to establish Akka cluster for your application.

_If your application is based on Lagom or Play, refer to [Deploying Microservices to Kubernetes](https://tech-hub.lightbend.com/guides/k8s-microservices) for information on deploying it to Kubernetes, including how to deploy Cassandra for the purpose of Akka Persistence and service discovery between Lagom apps. It is worth noting that the Akka cluster setup for Lagom based applications follows the same steps outlined by this guide._

## Prerequisites

* JDK8+
* [Docker](https://www.docker.com/)
* Either [sbt](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/)
* An existing, running Kubernetes cluster.
* The kubernetes CLI tool `kubectl` is installed and configured to point to the existing Kubernetes cluster.
* Docker environment variables are configured to point to Docker registry used by Kubernetes cluster. This will ensure the Docker images we built in this guide will be available to the Kubernetes cluster.
* An existing clustered Akka based application to deploy, built using sbt or Maven.

## Overview

First, we will need to containerize the application and publish it to the Docker registry used by the Kubernetes cluster. This guide will show how to configure both sbt and Maven to perform this task.

Once our image is published, we will utilize Kubernetes [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to deploy the application. Using StatefulSet, given a service named `myapp` and `3` replicas, Kubernetes will start `3` [Pods](https://kubernetes.io/docs/concepts/workloads/pods/pod/) with the names `myapp-0`, `myapp-1`, and `myapp-2`. These Pod names will be registered in the Kubernetes DNS, such that they can be resolved by the pods within the same StatefulSet. This would mean `myapp-0` as a host name can be resolved within the `myapp-2` pod, for example.

Based on the above example, the container `myapp-0`, `myapp-1`, and `myapp-2` can be used as the seed node to form the Akka cluster within Kubernetes. Each Pod will also need to expose the Akka remoting port so it is accessible from a different Pod instance. We will utilize Kubernetes [Service](https://kubernetes.io/docs/concepts/services-networking/service/) to achieve this.

Our overall steps will be:

1. [Naming your application](#1-naming-your-application)
2. [Handling environment variables](#2-handling-environment-variables)
3. [Publishing to a Docker registry](#3-publishing-to-a-docker-registry)
4. [Creating a Kubernetes Service for Akka remoting](#4-creating-kubernetes-service-for-akka-remoting)
5. [Creating a Kubernetes StatefulSet resource](#5-creating-kubernetes-statefulset-resource)

We have provided an example app which illustrates all the steps described above. The example app can be found in the `akka-cluster-example` GitHub project, and the `README.md` provides the instructions to deploy the example app to Kubernetes:

https://github.com/typesafehub/prod-suite-management-doc/tree/master/guides/akka-cluster-kubernetes-k8s-deploy/akka-cluster-example

## 1. Naming your application

In this guide we will refer to the application name as `myapp` - please substitute the application name with the actual name of the application you are going to deploy.

The application name:

* is referenced in the sbt or Maven build file and forms the Docker image name; and
* defines the Kubernetes StatefulSet and Service name.


## 2. Handling environment variables

To establish an Akka cluster within the Kubernetes container, a later section will show you how to add the following system properties necessary for containerizing your application:

```
-Dakka.actor.provider=cluster
-Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")"
-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT"
$(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done)
-DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME}
```

Some of the system properties values are derived from environment variables, i.e. `AKKA_REMOTING_BIND_HOST` and `AKKA_SEED_NODES`. These environment variables will be supplied by the Kubernetes StatefulSet.

| Environment Variable     | Description                                                                                                                                     | Example Value                                                                                                                            |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| AKKA_REMOTING_BIND_HOST  | The hostname of the Kubernetes Pod.                                                                                                             | `myapp-2.myapp.default.svc.cluster.local`                                                                                                |
| AKKA_REMOTING_BIND_PORT  | The Akka remoting port.                                                                                                                         | `2551`                                                                                                                                   |
| AKKA_SEED_NODES          | The hostname of the containers which forms the seed nodes within StatefulSet. Three containers are specified for the sake of resiliency         | `myapp-0.myapp.default.svc.cluster.local:2551,myapp-1.myapp.default.svc.cluster.local:2551,myapp-2.myapp.default.svc.cluster.local:2551` |
| AKKA_ACTOR_SYSTEM_NAME   | The name of the `ActorSystem` of your application. For the purpose of this guide, we'll match the `ActorSystem` name with the application name. | `myapp`                                                                                                                                  |

Ensure the `ActorSystem` name in the application is set based on the value specified by the system property `actorSystemName`, for example:

```
val actorSystemName =
  sys.props.getOrElse(
    "actorSystemName",
    throw new IllegalArgumentException("Actor system name must be defined by the actorSystemName property")
  )

val actorSystem = ActorSystem(actorSystemName)
```


## 3. Publishing to a Docker registry

Next, we will containerize the application and publish its Docker image. Please proceed with either [sbt instructions](#3-1-publishing-to-a-docker-registry-sbt) or [Maven instructions](#3-2-publishing-to-a-docker-registry-maven) accordingly.


### 3.1 Publishing to a Docker registry - sbt

We will be using the [sbt native packager](http://www.scala-sbt.org/sbt-native-packager/) plugin to containerize the application.

Enable sbt native packager in your project by adding the following line in the `project/plugins.sbt`:

```
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")
```

Add the following import in the `build.sbt`:

```
import com.typesafe.sbt.packager.docker._
```

Next, follow the steps for a [sbt multi-module project](#3-1-1-sbt-multi-module-project) or [sbt single-module project](#3-1-2-sbt-single-module-project).


#### 3.1.1 sbt multi-module project

@@@ note
Follow this section if your application is part of a multi-module sbt project.
@@@

The main steps include:

1. Enabling a packaging plugin.
2. Configuring Docker settings.

**Enabling a packaging plugin**

You may choose to enable [JavaAppPackaging](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html) or [JavaServerAppPackaging](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_server/index.html) plugin provided by sbt native packager.

The `JavaServerAppPackaging` plugin provides all the features provided by the `JavaAppPackaging` plugin with some additional [server features](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_server/index.html#features) such as daemon user/group support and support for `/etc/default`.

Please note that you must choose to enable either `JavaAppPackaging` or `JavaServerAppPackaging` - you _cannot_ enable both.

To enable the JavaAppPackaging plugin, add the `enablePlugins` instruction to the module declaration in the `build.sbt`:

```
lazy val myApp = project("my-app")
  // Add the following line to the module definition
  .enablePlugins(JavaAppPackaging)
```

To enable JavaServerAppPackaging instead:

```
lazy val myApp = project("my-app")
  // Add the following line to the module definition
  .enablePlugins(JavaServerAppPackaging)
```

**Configuring Docker settings**

We will now configure the Docker settings required to containerize the application.

Add a `dockerEntrypoint` to module settings that supplies the system properties necessary to containerize the application:

```
lazy val myApp = project("my-app")
  .settings(
    // Add the following line within the module settings
    dockerEntrypoint ++= Seq(
      """-Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")"""",
      """-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT"""",
      """$(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done)""",      
      "-Dakka.io.dns.resolver=async-dns",
      "-Dakka.io.dns.async-dns.resolve-srv=true",
      "-Dakka.io.dns.async-dns.resolv-conf=on"
    )
```

As part of building the Docker image, sbt native packager will provide its own Docker entry point script to start the application which accepts additional arguments. When system properties are presented as part of the arguments, they will be appended to the JVM options when the application is started within the container.

Next, we will transform the generated Dockerfile `ENTRYPOINT` instruction:

```
lazy val myApp = project("my-app")
  .settings(
    // Add the following line within the module settings
    dockerCommands :=
      dockerCommands.value.flatMap {
        case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
        case v => Seq(v)
      }
   )
```

As part of the Docker build, sbt native packager will also generate a startup script for the application. This startup script is referenced by the `ENTRYPOINT` command in the Dockerfile, i.e. `ENTRYPOINT ["bin/myapp"]`. Note the value of the `ENTRYPOINT` is an array - this is the `exec` form declaration of the `ENTRYPOINT`.

The script above transforms `ENTRYPOINT` from `exec` form to `shell` form. In the `shell` form, the `ENTRYPOINT` is declared as a simple string value. This string value will be executed by the container's shell using `sh -c`. The `shell` form is required to ensure the environment variables declared in the `dockerEntrypoint` argument is sourced from the shell within the Docker container. Refer to Docker [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint) documentation for the difference between `exec` and `shell` form.

The container image will be published to the the default Docker repository specified by sbt native packager. To publish to a different repository, set the `dockerRepository` setting to the repository you wish to publish. Please note this is an optional step. The Kubernetes deployment in this guide expects the image to be located at `mygroup/myapp`. To match the repository we need to set the `dockerRepository` to `mygroup`:

```
lazy val myApp = project("my-app")
  .settings(
    // Add the following line within the module settings
    dockerRepository := Some("mygroup")
  )
```

When the image is published, the version tag will be derived from the sbt project version. However it is also possible to publish both the `latest` tag and the version tag by enabling the `dockerUpdateLatest` setting. Please note this is an optional step. The Kubernetes deployment in this guide expects the `latest` image tag to be present. To match this expectation we need to enable `dockerUpdateLatest` setting:

```
lazy val myApp = project("my-app")
  .settings(
    // Add the following line within the module settings
    dockerUpdateLatest := true
  )
```

Execute the following command to containerize and publish your application's Docker image:

```bash
sbt docker:publishLocal
```

Additional sbt settings documentation to control the Docker image build process is available at the [Docker Plugin](http://www.scala-sbt.org/sbt-native-packager/formats/docker.html?highlight=dockercommand) documentation page of the sbt native packager.

#### 3.1.2 sbt single-module project

@@@ note
Follow this section if your application is a single-module sbt project.
@@@

**Enabling a packaging plugin**

You may choose to enable [JavaAppPackaging](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/index.html) or [JavaServerAppPackaging](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_server/index.html) plugin provided by sbt native packager.

The `JavaServerAppPackaging` plugin provides all the features provided by the `JavaAppPackaging` plugin with some additional [server features](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_server/index.html#features) such as daemon user/group support and support for `/etc/default`.

Please note that you must choose to enable either `JavaAppPackaging` or `JavaServerAppPackaging` - you _cannot_ enable both.

Enable the JavaAppPackaging plugin by adding the following line to the `build.sbt`:

```
enablePlugins(JavaAppPackaging)
```

Alternatively to enable JavaServerAppPackaging instead:

```
enablePlugins(JavaServerAppPackaging)
```

**Configuring Docker settings**

We will now configure the Docker settings required to containerize the application.

Add a `dockerEntrypoint` to module settings that supplies the system properties necessary to containerize the application:

```
dockerEntrypoint ++= Seq(
  """-Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")"""",
  """-Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT"""",
  """$(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done)""",
  "-Dakka.io.dns.resolver=async-dns",
  "-Dakka.io.dns.async-dns.resolve-srv=true",
  "-Dakka.io.dns.async-dns.resolv-conf=on"
)
```

As part of building the Docker image, sbt native packager will provide its own Docker entry point script to start the application which accepts additional arguments. When system properties are presented as part of the arguments, they will be appended to the JVM options when the application is started within the container.

Add the following `dockerCommands` declaration for sbt to use when generating a Dockerfile `ENTRYPOINT` instruction:

```
dockerCommands :=
  dockerCommands.value.flatMap {
    case ExecCmd("ENTRYPOINT", args @ _*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
    case v => Seq(v)
  }
```

The script above transforms `ENTRYPOINT` from `exec` form to `shell` form. The `shell` form is required to ensure the environment variables declared in the `dockerEntrypoint` argument is sourced from the shell within the Docker container. Refer to Docker [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint) documentation for the difference between `exec` and `shell` form.

The container image will be published to the the default Docker repository specified by sbt native packager. To publish to a different repository, set the `dockerRepository` setting to the repository you wish to publish. Please note this is an optional step. The Kubernetes deployment in this guide expects the image to be located at `mygroup/myapp`. To match the repository we need to set the `dockerRepository` to `mygroup`:

```
dockerRepository := Some("mygroup")
```

When the image is published, the version tag will be derived from the sbt project version. However it is possible to also publish both the `latest` tag and the version tag by enabling the `dockerUpdateLatest` setting. Please note this is an optional step. The Kubernetes deployment in this guide expects the `latest` image tag to be present. To match this expectation we need to enable `dockerUpdateLatest` setting:

```
dockerUpdateLatest := true
```

Execute the following command to containerize and publish your application's Docker image:

```bash
sbt docker:publishLocal
```

Additional sbt setting documentation to control the Docker image build process is available at the [Docker Plugin](http://www.scala-sbt.org/sbt-native-packager/formats/docker.html?highlight=dockercommand) documentation page from sbt native packager.

#### 3.1.3 Recommended: Using a smaller Docker base image

@@@ note
This is an optional but highly recommended step that allows you to achieve a smaller memory footprint for your service.
@@@

sbt native packager uses [openjdk:latest](https://hub.docker.com/_/openjdk/) image from the Docker Hub by default. To reduce the image size and the container startup time in Kubernetes, it is recommended to use a base image with smaller footprint. This is accomplished by deriving an image from [openjdk:8-jre-alpine](https://hub.docker.com/_/openjdk/) with `bash` installed as the Docker entrypoint script generated by sbt native packager requires `bash` to be present.

Create the image using the following command:

```bash
cat <<EOF | docker build -t local/openjdk-jre-8-bash:latest -
FROM openjdk:8-jre-alpine
RUN apk --no-cache add --update bash coreutils curl
EOF
```

Once created, this image can be used as a base image by specifying the `dockerBaseImage` setting in the `build.sbt`. For the multi-module project enable the `dockerBaseImage` setting as such:

```
lazy val myApp = project("my-app")
  .settings(
    // Add the following line within the module settings
    dockerBaseImage := "local/openjdk-jre-8-bash"
  )
```

For single-module project, add the following line to the `build.sbt`:

```
dockerBaseImage := "local/openjdk-jre-8-bash"
```


### 3.2 Publishing to a Docker registry - Maven

We will be using the [fabric8](https://dmp.fabric8.io/) Maven plugin to containerize the application.

Next, follow the steps for a [Maven multi-module project](#3-2-1-maven-multi-module-project) or [Maven single-module project](#3-2-2-maven-single-module-project).


#### 3.2.1 Maven multi-module project

@@@ note
Follow this section if your application is part of a multi-module Maven project.
@@@

Add the following plugin settings to the root project `pom.xml` to register the fabric8 Maven plugin:

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

When building a container image for the application, the base image [openjdk:8-jre-alpine](https://hub.docker.com/_/openjdk/) will be used to provide a working JRE 8 installation for the application to run on. The container image name expression is `%g/%a:%l` where `%g`, `%a`, and `%l` are Maven project's group id, artefact id, and the image tag. When published, our image will tagged with `latest` and `${project.version}`

Add the following plugin settings on the `pom.xml` under the application's module directory:

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
                        java -cp '/maven/*' -DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME} -Dakka.actor.provider=cluster -Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")" -Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" $(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done) com.mycompany.MainClass
                    </entryPoint>
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

The fabric8 Maven plugin will place all the jar files under `/maven` directory which need to be added to the JVM's classpath. Also note that we have added the Akka clustering related system properties as part of the JVM options.

Replace `com.mycompany.MainClass` with the actual main class of your application. In Akka applications, this would usually be the class where you start your `ActorSystem`.

Execute the following command to containerize and publish your application's Docker image:

```bash
mvn clean package docker:build
```


#### 3.2.2 Maven single-module project

@@@ note
Follow this section if your application is built using a single-module Maven project, i.e. single `pom.xml` located at the root of the project's directory.
@@@

Add the following plugin settings on the `pom.xml` under project root directory to register the fabric8 Maven plugin:

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
                        java -cp '/maven/*' -DactorSystemName=${AKKA_ACTOR_SYSTEM_NAME} -Dakka.actor.provider=cluster -Dakka.remote.netty.tcp.hostname="$(eval "echo $AKKA_REMOTING_BIND_HOST")" -Dakka.remote.netty.tcp.port="$AKKA_REMOTING_BIND_PORT" $(IFS=','; I=0; for NODE in $AKKA_SEED_NODES; do echo "-Dakka.cluster.seed-nodes.$I=akka.tcp://$AKKA_ACTOR_SYSTEM_NAME@$NODE"; I=$(expr $I + 1); done) com.mycompany.MainClass
                    </entryPoint>                    
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

When building a container image for the application, the base image [openjdk:8-jre-alpine](https://hub.docker.com/_/openjdk/) will be used to provide a working JRE 8 installation for the application to run on. The container image name expression is `%g/%a:%l` where `%g`, `%a`, and `%l` are Maven project's group id, artefact id, and the image tag. When published, our image will tagged with `latest` and `${project.version}`

The fabric8 Maven plugin will place all the jar files under `/maven` directory which need to be added to the JVM's classpath. Also note that we have added the Akka clustering related system properties as part of the JVM options.

Replace `com.mycompany.MainClass` with the actual main class of your application. Usually in Akka applications this would be the class where you start your `ActorSystem`.

Execute the following command to containerize and publish your application's Docker image:

```bash
mvn clean package docker:build
```

## 4. Creating a Kubernetes Service for Akka remoting

Next, we will declare the Akka remoting port we'd like to expose from our Pod using Kubernetes Service so it can be referenced from one Pod to another.

Akka Cluster uses Akka Remoting for all of it's across-network communication, and while one would not be using Akka Remoting directly in your application, we need to configure the port on which it should be listening for connections. The Akka Cluster tools will take it from there.

Execute the following command to create Kubernetes Service for the application's Akka remoting port:

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

All Pods that have the label `app` with the value of `myapp` will expose the TCP port `2551`.

To view the list of Kubernetes services, use the following command:

```bash
kubectl get services
```

We can also filter the Kubernetes services based on our created label `app=myapp`:

```bash
kubectl get services --selector=app=myapp
```

## 5. Creating a Kubernetes StatefulSet resource

Once we have the application containerized and published, and the Kubernetes Service declared for the application's Akka remoting port, we are ready to deploy the application.

Execute the following command to deploy the application:

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
                "value": "myapp"
              },
              {
                "name": "AKKA_REMOTING_BIND_PORT",
                "value": "2551"
              },
              {
                "name": "AKKA_REMOTING_BIND_HOST",
                "value": "$HOSTNAME.myapp.default.svc.cluster.local"
              },
              {
                "name": "AKKA_SEED_NODE_PORT",
                "value": "2551"
              },
              {
                "name": "AKKA_SEED_NODE_HOST_0",
                "value": "myapp-0.myapp.default.svc.cluster.local"
              },
              {
                "name": "AKKA_SEED_NODE_HOST_1",
                "value": "myapp-1.myapp.default.svc.cluster.local"
              },
              {
                "name": "AKKA_SEED_NODE_HOST_2",
                "value": "myapp-2.myapp.default.svc.cluster.local"
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

The StatefulSet has `1` replica. You may adjust this number to the number of instances you desire. Alternatively, you can deploy the with `1` replica to begin with, and once the first service is started you may adjust this number by modifying the StatefulSet:

```bash
kubectl scale statefulsets myapp --replicas=3
```

The Pods for the StatefulSet will be initialized with the image `mygroup/myapp`. Adjust this image name to match the actual image published by the sbt or Maven build. The `imagePullPolicy` is set to `Never` to ensure only image published to the Docker registry is used. If the image doesn't exist in the Docker registry, the StatefulSet creation will fail with an error.

Each Pod in the StatefulSet will expose port `2551` as declared by the `containerPort` named `akka-remote`.

The StatefulSet exposes `myapp-0`, `myapp-1`, and `myapp-2` as the cluster's seed nodes. As StatefulSet guarantees the startup order of the container, `myapp-0` will be started first and become the cluster leader. Should `myapp-0` is restarted for any reasons, the new instance of `myapp-0` will look for `myapp-1` or `myapp-2` first to join the cluster, ensuring no split cluster upon `myap-0` restart.

Please adjust the CPU and memory resources to match the requirements of the application.

The `env` property of the StatefulSet contains list of environment variables to be passed into the application when it starts. These environment variables will be used to populate the system properties to bootstrap the Akka cluster required by the application. Note that the environment variable `AKKA_SEED_NODE_HOST` is set to `myapp-0`, which means that `myapp-0` is used as the seed node to establish the Akka cluster.

We will be using port `2551` to inform Kubernetes that the application is ready as configured by the `readinessProbe`. Depending on the nature of your application, the application might not be ready when the `ActorSystem` starts up. You may opt to inform Kubernetes using a different means of [readinessProbe](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/).

Once the StatefulSet is created, you can view the created pods using the following command:

```bash
kubectl get pods --selector=app=myapp
```

Use the following command to view the log messages from a particular pod:

```bash
kubectl logs -f <pod name>
```


## Conclusion

At this point you will have your application containerized and deployed in Kubernetes. The Akka cluster required by your application will be established when the application is deployed, and the new Pod instance will join the cluster as the number of replicas that are scaled up.
