# How to setup Akka cluster to Kubernetes

## The problem

You've created an application using [Akka](http://akka.io/) with [Akka Clustering](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) enabled. After evaluating all of your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: how do you setup an Akka cluster on Kubernetes? Finally, running an application on Kubernetes requires containerization. How exactly do we deploy these systems to Kubernetes?

## The solution

In this guide, we'll cover the steps required to deploy an Akka based application to Kubernetes.

The guide will show how to containerize the application using [SBT](http://www.scala-sbt.org/) or [Maven](https://maven.apache.org/) build tool.

This guide will also show how to configure Kubernetes [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) to establish Akka cluster for your application.

### Prerequisites

<todo>

### Publishing to Docker registry - SBT

<todo>

### Publishing to Docker registry - Maven

<todo>

### Creating Kubernetes Service for Akka remoting

<todo>


### Creating Kubernetes StatefulSet resource

<todo>


## Conclusion

<todo putting it all together>
