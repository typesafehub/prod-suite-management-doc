# How to setup Akka cluster to Kubernetes

## The problem

You've created an application using [Akka](http://akka.io/) with [Akka Clustering](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) enabled. After evaluating all of your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: how do you setup an Akka cluster on Kubernetes? Finally, running an application on Kubernetes requires containerization. How exactly do we deploy these systems to Kubernetes?

## The solution

In this guide, we'll cover the steps required to deploy an Akka based application to Kubernetes.

The guide will show how to containerize the application using [SBT](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/) build tool.

This guide will also show how to configure Kubernetes [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to establish Akka cluster for your application.


--- The lines below here is still not written nicely, skeleton sections only.

### Prerequisites

* Existing Kubernetes cluster with Docker registry.
* `kubectl` is configured to point to existing Kubernetes cluster.
* `docker` command is installed with Docker environment configured to point to Docker registry used by Kubernetes cluster.
* JDK8+

Project has either SBT or Maven

### How it works

Use StatefulSet to deploy.
StatefulSet defines environment variable.
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

<todo>


### Creating Kubernetes StatefulSet resource

<todo>


## Conclusion

<todo putting it all together>
