# Deploying Lagom Microservices on DC/OS

<style type="text/css">
  pre.code-bash::before {
    content: '$ ';
    color: #009900;
    font-weight: bold;
  }
</style>

[Lagom](http://www.lagomframework.com/) is a flexible microservices framework that makes it quick and easy to
build, test, and deploy your systems with confidence. [DC/OS](https://dcos.io/), an open-source distributed operating
system based on [Apache Mesos](http://mesos.apache.org/), provides features such as DNS, routing, and 
clustering services that Lagom systems use in production. This guide uses the 
[Lagom Chirper](https://github.com/lagom/lagom-java-maven-chirper-example) example, which is already configured to run 
on DC/OS. It points out the configuration you would need to make in your Lagom system. And, it provides step-by-step 
instructions for running the Chirper example on DC/OS.

## The Challenge

Deploying a Lagom service on DC/OS presents the following challenges:
 
* Lagom's [Persistent Entity API](https://www.lagomframework.com/documentation/1.3.x/java/PersistentEntityCassandra.html)
leverages [Akka Cluster](http://doc.akka.io/docs/akka/2.5.3/scala/common/cluster.html). When deployed 
to an orchestrated environment such as DC/OS, you need to make sure that the clusters are bootstrapped correctly.
* Lagom applications use a [Service Locator](https://www.lagomframework.com/documentation/1.3.x/java/ServiceLocator.html)
for communication between services. The locator needs to work together with the Mesos DNS service.

## The Solution

This guide covers the steps required to deploy a Lagom microservices system to DC/OS. It provides an overview on
the strategy for deploying to a DC/OS cluster and then dives into the commands and configuration required.

#### The Setup

This guide demonstrates the solution using the [Chirper (Maven)](https://github.com/lagom/lagom-java-maven-chirper-example) Lagom
example app. Before continuing, make sure you have the following installed and configured on your local
machine:

* JDK8+
* [Docker](https://www.docker.com/)
* Access to a DC/OS environment with connectivity to it via the `dcos` command line tool.
* A local host entry (`/etc/hosts`) pointing `chirper.dcos` to a public node in your DC/OS cluster 
* A clone of the [Lagom Chirper (Maven) repository](https://github.com/lagom/lagom-java-maven-chirper-example)
* [Maven](https://maven.apache.org/)
* Push access to a Docker registry (from the command line)
* Pull access to a Docker registry (from DC/OS)

Additionally, if you wish to build and publish your own images instead of using the provided ones:

#### About Chirper

Chirper is a Lagom-based microservices system that aims to simulate a Twitter-like website. This guide will demonstrate 
how artifacts built using Maven are deployed to DC/OS. Chirper has already been configured for deployment on DC/OS. 
The guide below details this configuration so that you can emulate it in your own project.

#### Service Location

Lagom makes use of a [Service Locator](https://www.lagomframework.com/documentation/1.4.x/java/ServiceLocator.html) to
call other services within the system. Chirper is configured to use the [reactive-lib](https://github.com/lightbend/reactive-lib/)
project to provide a service locator that takes advantage of [Mesos-DNS](https://dcos.io/docs/1.7/usage/service-discovery/mesos-dns/).

Chirper's services are configured in each of its `platform.conf` files to enable the service locator. The `platform.conf` files
are included by `application.conf`, so you can add it directly there in your own application.

##### Dependencies

```xml
<dependency>
    <groupId>com.lightbend.rp</groupId>
    <artifactId>reactive-lib-service-discovery-lagom14-java_${scala.binary.version}</artifactId>
    <version>$version</version>
</dependency>
```

##### Configuration

**application.conf**

```hocon
akka.io {
  io {
    dns {
      resolver = async-dns
      async-dns {
        provider-object = "com.lightbend.rp.asyncdns.AsyncDnsProvider"
        resolve-srv = true
        resolv-conf = on
      }
    }
  }
}

play.modules.enabled += com.lightbend.rp.servicediscovery.lagom.javadsl.ServiceLocatorModule
```

**marathon.json**

```json
...

"labels": {
    "ACTOR_SYSTEM_NAME": "friendservice",
    "HAPROXY_GROUP": "external",
    "HAPROXY_0_VHOST": "chirper.dcos",
    "HAPROXY_0_PATH": "/api/users"
}
      
...
```

_Refer to the various `marathon-resources/platform.conf` files in the Chirper repository for more details._

#### Akka Cluster Bootstrapping

Lagom's persistence APIs rely on Akka Persistence and thus Akka Cluster. This means that great care must be taken
when deploying these applications to ensure that they are bootstrapped correctly and do not form separate "islands" of
1-node clusters. To do this, Chirper is configured to use [Akka Management](https://github.com/akka/akka-management) 
 to bootstrap the cluster along with its Marathon API. Note the following dependencies and configuration that the
`friendservice` uses. All services that use clustering must be configured similarly.
 
##### Dependencies

```xml
<dependency>
    <groupId>com.lightbend.akka.management</groupId>
    <artifactId>akka-management_${scala.binary.version}</artifactId>
    <version>0.10.0</version>
</dependency>
<dependency>
    <groupId>com.lightbend.akka.management</groupId>
    <artifactId>akka-management-cluster-bootstrap_${scala.binary.version}</artifactId>
    <version>0.10.0</version>
</dependency>
<dependency>
    <groupId>com.lightbend.akka.discovery</groupId>
    <artifactId>akka-discovery-marathon-api_${scala.binary.version}</artifactId>
    <version>0.10.0</version>
</dependency>
```
 
##### Configuration

The following configuration configures Akka Management and Akka Remoting to bind on the correct ports, and configures
discovery to use the Marathon API. If a node fails to join the cluster after some period of time, it will also
cause the application to exit at which point Marathon will reschedule it.

**application.conf**

```hocon
play {
  akka.actor-system = "friendservice"
}

akka {
  actor {
    provider = cluster
  }

  cluster {
    shutdown-after-unsuccessful-join-seed-nodes = 40s
  }

  discovery {
    method = marathon-api
  }

  management {
    http {
      hostname = ${?HOST}
      port = ${?PORT_AKKAMGMTHTTP}
      bind-hostname = "0.0.0.0"
      bind-port = ${?PORT_AKKAMGMTHTTP}
    }
  }

  remote.netty.tcp {
    hostname = ${?HOST}
    port = ${?PORT_AKKAREMOTE}

    bind-hostname = "0.0.0.0"
    bind-port = ${?PORT_AKKAREMOTE}
  }
}

lagom.cluster.exit-jvm-when-system-terminated = on
```

**marathon.json**

```json
...

"container": {
    "docker": {
      "image": "...",
      "network": "BRIDGE",
      "portMappings": [
        { "containerPort": 0, "servicePort": 0, "name": "http" },
        { "containerPort": 0, "servicePort": 0, "name": "akkaremote" },
        { "containerPort": 0, "servicePort": 0, "name": "akkamgmthttp" }
      ]
    },
    "labels": {
      "ACTOR_SYSTEM_NAME": "friendservice",
    }
}

...
```

**FriendModule.java**

```java
import akka.actor.ActorSystem;
import akka.management.AkkaManagement$;
import akka.management.cluster.bootstrap.ClusterBootstrap$;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import play.Application;
import sample.chirper.friend.api.FriendService;

import javax.inject.Inject;

public class FriendModule extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindService(FriendService.class, FriendServiceImpl.class);
    bind(OnStart.class).asEagerSingleton();
  }
}

class OnStart {
  @Inject
  public OnStart(Application application, ActorSystem actorSystem) {
    doOnStart(application, actorSystem);
  }

  private void doOnStart(Application application, ActorSystem actorSystem) {
    if (application.isProd()) {
      AkkaManagement$.MODULE$.get(actorSystem).start();
      ClusterBootstrap$.MODULE$.get(actorSystem).start();
    }
  }
}
```

_Refer to the various `marathon-resources/platform.conf` and `deploy/marathon/chirper.json` files in the 
Chirper repository for more details. Be sure to consult the documentation for Akka Managent as well._

#### Dynamic Proxying & Ingress Routing

This guide uses [Marathon-LB](https://github.com/mesosphere/marathon-lb) to provide dynamic proxying and ingress
routing. This ensures that, for example, a request to `/api/chirpservice` is routed to `chirp-impl` while a request to
`/api/users` is routed to `friend-impl`. To do this, various labels must be set in the Marathon configuration. For 
example, `friend-impl` uses the following:

```
"labels": {
  "HAPROXY_GROUP": "external",
  "HAPROXY_0_VHOST": "chirper.dcos",
  "HAPROXY_0_PATH": "/api/users"
}
```

_Refer to `deploy/marathon/chirper.json` for more information on the Marathon-LB configuration._

## Local Cluster

The [DC/OS Vagrant](https://github.com/dcos/dcos-vagrant) project can be used to provision a local cluster on your
own machine using a number of virtual machines. You'll need to use a cluster with three private agents and
one public agent. Consult its README for more information. 

_Note that running a DC/OS cluster with Cassandra locally uses a lot of memory. We recommend you have at least 32GB of memory 
and close all unused programs. Because of this, it is often easier to use a real test cluster._

## Manual Deployment

Now that we've covered the various features required for deploying a Lagom application like Chirper to DC/OS, this
guide will detail the steps that must be performed.

Deploying Chirper requires the following actions:

1. Setup DC/OS
2. Install Cassandra
3. Install Marathon-LB
4. Build and Publish Chirper Docker images
5. Deploy Chirper
6. Verify Deployment

Let's take a look at how these tasks can be performed from your own terminal. Make sure 
you've `cd`'d into your clone of the Chirper repository before proceeding.

## 1. Setup DC/OS

You can deploy Chirper to any number of DC/OS environments. The steps below assume that your `dcos` command line tool
is configured to point at your cluster, and that an alias for `chirper.dcos` is setup in your system's `/etc/hosts` file.
Production deployments should substitute `chirper.dcos` with a valid hostname.

_If you are using the provided DC/OS Vagrant configuration, the `chirper.dcos` alias will be setup for you._

<pre class="code-bash prettyprint prettyprinted">
dcos node
</pre>

```
   HOSTNAME           IP                           ID                    TYPE             
192.168.65.111  192.168.65.111  24c481fe-92ed-456a-a01c-3d282169ea08-S3  agent
192.168.65.121  192.168.65.121  24c481fe-92ed-456a-a01c-3d282169ea08-S4  agent
192.168.65.60   192.168.65.60   24c481fe-92ed-456a-a01c-3d282169ea08-S5  agent
master.mesos.   192.168.65.90     24c481fe-92ed-456a-a01c-3d282169ea08   master (leader)
```

## 2. Install Cassandra

Cassandra is freely available from the [DC/OS Universe](https://universe.dcos.io/). To install it, run the following:

<pre class="code-bash prettyprint prettyprinted">
CASSANDRA_OPTIONS=$(mktemp) && \
echo '{ "nodes": { "mem": 2048 } }' > "$CASSANDRA_OPTIONS" && \
dcos package install cassandra "--options=$CASSANDRA_OPTIONS"
</pre>

You should see the following output:

```
By Deploying, you agree to the Terms and Conditions https://mesosphere.com/catalog-terms-conditions/#certified-services
Default configuration requires 3 agent nodes each with: 0.5 CPU | 4096 MB MEM | 1 10240 MB Disk
Continue installing? [yes/no] yes
Installing Marathon app for package [cassandra] version [2.0.2-3.0.14]
Installing CLI subcommand for package [cassandra] version [2.0.2-3.0.14]
New command available: dcos cassandra
The DC/OS Apache Cassandra service is being installed!

	Documentation: https://docs.mesosphere.com/service-docs/cassandra/
	Issues: https://docs.mesosphere.com/support/
```

> If this is a production deployment, please be sure to consult the [DC/OS Cassandra](https://docs.mesosphere.com/service-docs/cassandra/2.0.2-3.0.14/)
documentation as you'll need to provide a configuration tailored to your environment. You may also choose to run Cassandra
outside of DC/OS and have it be offered "as-a-service" to applications in your DC/OS cluster.

## 3. Install Marathon-LB

Marathon-LB is also available from the [DC/OS Universe](https://universe.dcos.io/). To install it, run the following:

<pre class="code-bash prettyprint prettyprinted">
dcos package install marathon-lb
</pre>

You should see the following output:

```
By Deploying, you agree to the Terms and Conditions https://mesosphere.com/catalog-terms-conditions/#community-services
We recommend at least 2 CPUs and 1GiB of RAM for each Marathon-LB instance.

*NOTE*: For additional ```Enterprise Edition``` DC/OS instructions, see https://docs.mesosphere.com/administration/id-and-access-mgt/service-auth/mlb-auth/
Continue installing? [yes/no] yes
Installing Marathon app for package [marathon-lb] version [1.11.1]
Marathon-lb DC/OS Service has been successfully installed!
See https://github.com/mesosphere/marathon-lb for documentation.
```

## 4. Build and Publish Chirper Docker images

Chirper is configured to build Docker images for each of its microservices. After building the images, they'll need to 
be published to a Docker registry.

> For general assistance on setting up your Lagom build please refer to ["Defining a Lagom Build" in the Lagom documentation](https://www.lagomframework.com/documentation/1.4.x/scala/LagomBuild.html).

----------------------------------

###### Maven

By using 
[fabric8's docker-maven-plugin](https://dmp.fabric8.io/), these images will be built and published to the local Docker
repository. The command below will build Chirper and the Docker images using Maven and this plugin.

> Note that if you see a `[ERROR] DOCKER> Unable to pull...` error with the following then you'll need to update your Java version [as per a known issue with Java TLS](https://github.com/fabric8io/docker-maven-plugin/issues/845#issuecomment-324249997).

<pre class="code-bash prettyprint prettyprinted">
mvn -DbuildTarget=marathon clean package docker:build
</pre>

_Refer to the various `pom.xml` files in the [Chirper repository](https://github.com/lagom/lagom-java-maven-chirper-example) for more details._

----------------------------------

Next, inspect the images that are available. Note that the various Chirper services all have their own image. These will
be deployed to the cluster.
<pre class="code-bash prettyprint prettyprinted">
docker images
</pre>

```
REPOSITORY                             TAG              IMAGE ID      CREATED       SIZE
chirper-marathon/front-end             1.1.14-SNAPSHOT  65837188dde1  20 hours ago  767MB
chirper-marathon/friend-impl           1.1.14-SNAPSHOT  a8a589c6c16f  20 hours ago  845MB
chirper-marathon/chirp-impl            1.1.14-SNAPSHOT  d3e32819f040  20 hours ago  844MB
chirper-marathon/activity-stream-impl  1.1.14-SNAPSHOT  072cb98868d5  20 hours ago  760MB
```

Then, the images need to be pushed to a Docker registry. This assumes that `docker login` has been used to
authenticate as necessary. Be sure to update the `VERSION` and `REGISTRY` variables as necessary.

<pre class="code-bash prettyprint prettyprinted">
export VERSION=1.0.0-SNAPSHOT && export REGISTRY=yourregistry.acme.org && \
docker tag "chirper-marathon/friend-impl:$VERSION" "$REGISTRY/friend-impl:$VERSION" && \
docker tag "chirper-marathon/activity-stream-impl:$VERSION" "$REGISTRY/activity-stream-impl:$VERSION" && \
docker tag "chirper-marathon/front-end:$VERSION" "$REGISTRY/front-end:$VERSION" && \
docker tag "chirper-marathon/chirp-impl:$VERSION" "$REGISTRY/chirp-impl:$VERSION" && \
docker push "$REGISTRY/friend-impl:$VERSION" && \
docker push "$REGISTRY/activity-stream-impl:$VERSION" && \
docker push "$REGISTRY/front-end:$VERSION" && \
docker push "$REGISTRY/chirp-impl:$VERSION"
</pre>
## 5. Deploy Chirper

Finally, Chirper can be deployed to the cluster. If you're using a private
Docker registry, be sure to consult the [DC/OS Documentation](https://dcos.io/docs/latest/deploying-services/private-docker-registry/)
for configuring your cluster to access it.

_Be sure to modify the `deploy/marathon/chirper.json` file to point to your own images._

<pre class="code-bash prettyprint prettyprinted">
dcos marathon group add < deploy/marathon/chirper.json
</pre>

```
Created deployment 702c6895-2a33-4ed3-a628-7a2b5b35d0f4
```

> Be sure to inspect the contents of `chirper.json` and modify accordingly for your own application and environment.
For instance, `CASSANDRA_SERVICE_NAME`, and `APPLICATION_SECRET` must be specified 
appropriately for the environment your application is deployed to.

## 6. Verify Your Deployment

After a few minutes, Chirper and all of its dependencies will be running in the cluster. After verifying this with the
DC/OS dashboard, open [http://chirper.dcos](http://chirper.dcos) in your browser.

_If you used a different hostname, be sure to use that instead._

## 7. Scaling Your Deployment

Now that Chirper is deployed, the scale of its services can be adjusted. Below, the `dcos` command will be used to
scale `friendservice` to three instances and verify successful Akka Cluster formation.

<pre class="code-bash prettyprint prettyprinted">
dcos marathon app update /chirper/friendservice instances=3
</pre>

```
Created deployment 57c13096-2c89-4917-a985-28fae2c18777
```

<pre class="code-bash prettyprint prettyprinted">
dcos task
</pre>

```
NAME                     HOST             USER   STATE  ID                                                            MESOS ID                                 
activityservice.chirper  192.168.65.111   root     R    chirper_activityservice.9b01d43e-c314-11e7-a4d4-70b3d5800001  ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S5  
cassandra                192.168.65.121   root     R    cassandra.f1595d39-c313-11e7-a4d4-70b3d5800001                ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S4  
chirpservice.chirper     192.168.65.121   root     R    chirper_chirpservice.9b01861d-c314-11e7-a4d4-70b3d5800001     ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S4  
friendservice.chirper    192.168.65.111   root     R    chirper_friendservice.9b015f0c-c314-11e7-a4d4-70b3d5800001    ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S5  
friendservice.chirper    192.168.65.111   root     R    chirper_friendservice.a868f62f-c315-11e7-a4d4-70b3d5800001    ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S5  
friendservice.chirper    192.168.65.121   root     R    chirper_friendservice.a8691d40-c315-11e7-a4d4-70b3d5800001    ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S4  
front-end.chirper        192.168.65.111   root     R    chirper_front-end.9b0110eb-c314-11e7-a4d4-70b3d5800001        ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S5  
marathon-lb              192.168.65.60    root     R    marathon-lb.48056504-c312-11e7-97a5-70b3d5800001              ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S7  
node-0-server            192.168.65.121  nobody    R    node-0-server__b1a19fad-56be-4650-81ce-9ff2f6c25282           ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S4  
node-1-server            192.168.65.131  nobody    R    node-1-server__f1820f91-70e6-4780-b1f2-5b47faa8cb3a           ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S6  
node-2-server            192.168.65.111  nobody    R    node-2-server__93c62119-8b85-41bd-985e-6d7141ca4be9           ac2d39b3-db25-4a9b-a3ef-cc3b97fb5bd4-S5  
```

<pre class="code-bash prettyprint prettyprinted">
dcos task log chirper_friendservice.a868f62f-c315-11e7-a4d4-70b3d5800001
</pre>

```
2017-11-06T17:13:18.027Z [info] akka.cluster.singleton.ClusterSingletonManager [sourceThread=friendservice-akka.actor.default-dispatcher-2, akkaTimestamp=17:13:18.027UTC, akkaSource=akka.tcp://friendservice@192.168.65.111:14858/user/readSideGlobalPrepare-FriendEventProcessor-singleton, sourceActorSystem=friendservice] - ClusterSingletonManager state change [Start -> Younger]
2017-11-06T17:13:18.126Z [info] akka.cluster.singleton.ClusterSingletonManager [sourceThread=friendservice-akka.actor.default-dispatcher-18, akkaTimestamp=17:13:18.126UTC, akkaSource=akka.tcp://friendservice@192.168.65.111:14858/user/cassandraOffsetStorePrepare-singleton, sourceActorSystem=friendservice] - ClusterSingletonManager state change [Start -> Younger]
2017-11-06T17:13:18.129Z [info] akka.cluster.singleton.ClusterSingletonManager [sourceThread=friendservice-akka.actor.default-dispatcher-3, akkaTimestamp=17:13:18.128UTC, akkaSource=akka.tcp://friendservice@192.168.65.111:14858/system/sharding/FriendEntityCoordinator, sourceActorSystem=friendservice] - ClusterSingletonManager state change [Start -> Younger]
```

## Conclusion

DC/OS provides many features that a Lagom system needs to run in production:
 
* By leveraging [Akka Management](https://github.com/akka/akka-management) Akka Clusters can be formed easily and correctly.
* The [reactive-lib](https://github.com/lightbend/reactive-lib/) project can be used to integrate with [Mesos-DNS](https://github.com/mesosphere/mesos-dns).
* Maven users can use [fabric8's docker-maven-plugin](https://dmp.fabric8.io/) to containerize their applications.
* [Marathon-LB](https://github.com/mesosphere/marathon-lb) can be configured to handle dynamic proxying and ingress routing.
* [Chirper](https://github.com/lagom/lagom-java-maven-chirper-example) can be referenced by any developer wishing to
deploy his or her Lagom or Akka cluster to DC/OS. It's a great example that takes advantage of many advanced features
of Akka, Lagom, and DC/OS!
