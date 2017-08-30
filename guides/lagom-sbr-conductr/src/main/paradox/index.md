# Deploying Lagom Microservices with Akka Split Brain Resolver (SBR)

[Lagom](http://www.lagomframework.com/) is an opinionated microservices framework that makes it quick and easy to
build, test, and deploy your systems with confidence. [Akka SBR](http://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html) handles network partitions automatically and is a feature of the Lightbend Reactive Platform exclusively available for Lightbend customers.

## The Challenge

A fundamental and common problem in distributed systems is that network partitions (split brain scenarios) and machine crashes are indistinguishable for the observer, i.e. a node can observe that there is a problem with another node, but it cannot tell if it has crashed and will never be available again or if there is a network issue that might or might not heal again after a while. Temporary and permanent failures are indistinguishable because decisions must be made in finite time, and there always exists a temporary failure that lasts longer than the time limit for the decision.

## The Solution

Lagom services combined with Akka SBR can help avoid the consequences of network partitions thereby adding further resilience to your system. This guide shows how to add Akka SBR to your Lagom service. As a follow on, the guide then demonstrates a network partition in action using ConductR, the [application management component of the Lightbend Enterprise Suite](https://conductr.lightbend.com/docs/2.1.x/Home).

## 1. Build your "Lagom with Scala" service

Follow the steps at [Using Lagom with Scala](https://www.lagomframework.com/get-started-scala.html)

## 2. Enable Akka Cluster & Akka SBR

There are various reasons why you may decide to use Akka Cluster with Lagom, including persistence and publish-subscribe.

> See the [Lagom documentation for more information on Lagom with Akka Cluster](https://www.lagomframework.com/documentation/1.3.x/scala/Cluster.html#Cluster).

In the following `build.sbt` of your service, Akka clustering is already added given the dependency on `lagomScaladslPersistenceCassandra`. Akka SBR is added via the `akka-split-brain-resolver` dependency.

@@snip [build.sbt](../../../lagom-scala-sbt/build.sbt) { #hello-lagom-impl-build }

> In order for the build to download Akka SBR you will require credentials. You find your credentials at https://www.lightbend.com/product/lightbend-reactive-platform/credentials including links to instructions of how to add the credentials to your build.

The next step is to enable the Split Brain Resolver by configuring it with akka.cluster.downing-provider-class in the configuration of the ActorSystem (*application.conf*):

@@snip [application.conf](../../../lagom-scala-sbt/hello-lagom-impl/src/main/resources/application.conf) { #hello-lagom-impl-conf }

The above configuration declares that Akka SBR will be used and it will use a `keep-majority` strategy. Akka SBR has a number of strategies and "keep majority" will down the unreachable nodes if the current node is in the majority part based on the last known membership information. Otherwise, "keep majority" will down the reachable nodes, i.e. the own part. If the parts are of equal size the part containing the node with the lowest address is kept.

The configuration also declares that Lagom should exit when Akka SBR makes a decision to down a node. We do this so that any orchestrator in use (such as ConductR, or Kubernetes) will restart the service elsewhere given a non-zero exit value.

> Consult the [Akka SBR documentation for more information on its available strategies](http://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html#strategies).

## 3. Deploy your service to ConductR

At this stage, we have a service that is ready to be deployed. Simply add [ConductR's plugin](https://github.com/typesafehub/sbt-conductr#sbt-conductr) to your build's `plugins.sbt` so that we can package and deploy your service:

@@snip [plugins.sbt](../../../lagom-scala-sbt/project/plugins.sbt) { #hello-lagom-plugins }

...and modify `HellolagomLoader.scala`'s import to import some wiring for ConductR:

@@snip [HellolagomLoader.scala](../../../lagom-scala-sbt/hello-lagom-impl/src/main/scala/com/example/hello/impl/HellolagomLoader.scala) { #HellolagomLoader-import }

...and then apply the wiring of ConductR components:

@@snip [HellolagomLoader.scala](../../../lagom-scala-sbt/hello-lagom-impl/src/main/scala/com/example/hello/impl/HellolagomLoader.scala) { #HellolagomLoader-load }

From the `sbt` project (press RETURN if you're still running the `runAll` command from having built "Lagom with Scala"), reload your project in order to realize its new settings:

```
sbt> reload
```

...and then build your a package of your service for ConductR:

```
sbt> hello-lagom-impl/bundle:dist
```

One last thing that we've done is to provide our service with its own [`lagom-scala-sbt/hello-lagom-impl/src/main/resources/logback.xml`](../../../lagom-scala-sbt/hello-lagom-impl/src/main/resources/logback.xml) file so that we can see what is happening with Akka remoting and Akka cluster during the split brain scenario (described next). These logging changes are down simply to allow this guide to show what is happening and are not mandatory.

## 4.Test Akka SBR

### Overview

> This test assumes ConductR, but its method can be applied to most orchestrator solutions, including Kubernetes.

To test Akka SBR, this guides assumes that ConductR's agent is running on 3 machines and that the `hello-lagom-impl` service has been loaded and run with a scale of 3 on its cluster (hint: `conduct load`/`conduct run --scale` - prefix everything with `dcos ` when using ConductR on DC/OS).
 
Akka SBR will be triggered upon encountering a network partition. To test SBR we must trigger a network partition by severing the network and upon completion of the test, the severed network must be reinstated. To trigger a network partition `iptables` can be used to drop incoming and outgoing traffic to Akka remoting port, effectively isolating the node where the traffic is dropped.

### Steps

#### 4.1. Find out the list of Akka remoting host and port from each cluster member

`conduct info hello` will provide information on the host ports that are bound to e.g.:

```
BUNDLE EXECUTIONS
-----------------
ENDPOINT     HOST          PID  STARTED  UPTIME  BIND_PORT  HOST_PORT
akka-remote  10.0.0.206  82393      Yes     47s      11026      11026
akka-remote  10.0.3.174  82448      Yes     39s      11043      11043
akka-remote  10.0.0.218  82509      Yes     30s      11027      11027
``` 

#### 4.2. Pick the node where you'd like to isolate

In this case we'll pick the `10.0.0.218` node which will have `11027` as its Akka remoting port.

#### 4.3. Isolate the node

From the `10.0.0.218` node's point of view the Akka remoting port traffic is:

* incoming on port `11027`, and
* outgoing on port `11026` and `11043`.

To drop traffic on these ports, we issue the following commands.

```bash
sudo iptables -A INPUT  -p tcp --destination-port 11027 -j DROP
sudo iptables -A OUTPUT -p tcp --destination-port 11026 -j DROP
sudo iptables -A OUTPUT -p tcp --destination-port 11043 -j DROP
```

#### 4.4. The Lagom instance on isolated node will be terminated

The logs of Lagom instances on `10.0.0.206` and `10.0.3.174` will eventually indicate the removal `10.0.0.218` from the cluster as it is no longer reachable.

The logs of Lagom instance on `10.0.0.218` will indicate the shutdown of its actor system. This is due to the `10.0.0.218` node being separated from the rest of the cluster, and the partition where the `10.0.0.218` node belongs has the minority number of nodes. Lagom will automatically shut down and exit the JVM with a non-zero exit status.

#### 4.5. Recovery

ConductR will automatically restart any service that terminates abnormally i.e. has a non-zero exit status. If the newly started instance is running on same host and port where the old instance was running (i.e. `10.0.0.218` port `11027`), the Akka Cluster will wait until it's possible to rejoin the seed nodes.

#### 4.6. Revert the node isolation

Once completed, revert the node isolation:

```bash
sudo iptables -D INPUT -p tcp --destination-port 11027 -j DROP
sudo iptables -D OUTPUT -p tcp --destination-port 11026 -j DROP
sudo iptables -D OUTPUT -p tcp --destination-port 11043 -j DROP
```

## Conclusion

This guide has shown how to add Akka clustering and its Split Brain Resolver to your Lagom service. The guide then went on to show how Akka SBR can be tested when combined with ConductR, although other orchestrator solutions, including Kubernetes, should behave similarly.

Network partitions will occur and Akka's SBR along with Lagom's automatic handling goes a long way to mitigate failure in your system.