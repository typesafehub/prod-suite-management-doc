# How to deploy microservices to Kubernetes

## The problem

You've created a brand new microservices system using [Lagom](http://www.lagomframework.com/). After evaluating all of 
your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it 
provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: 
since Lagom is based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/) 
and both are Highly Available (HA) by default, they require clustering. Lagom applications also need the ability to
locate other services running in its environment. Finally, running an application on Kubernetes requires 
containerization. How exactly are these complex systems deployed to Kubernetes?

## The solution

In this guide, the steps required to deploy a Lagom microservices system to your local Kubernetes cluster by
way of [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) will be covered.It'll also touch on the
modifications required to this recipe to run on [IBM BlueMix](), a cloud platform as a service (PaaS) built on Kubernetes.

### Prerequisites

The solution proposed here utilizes [Chirper](https://github.com/lagom/activator-lagom-java-chirper),
our sample Lagom system. The following prerequisites are required:

* JDK8+
* [Maven](https://maven.apache.org/)
* [Docker](https://www.docker.com/)
* [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) with connectivity to it
  via the `kubectl` command line tool
* A clone of the [Lagom Chirper repository](https://github.com/lagom/activator-lagom-java-chirper)

### Our approach

This guide will be covering the resources required to run a Lagom system on Kubernetes. This will detail the various resource 
types and their purpose, as well as how they need to be declared.

### About Chirper

Chirper is a Lagom-based microservices system that aims to simulate a Twitter-like website. It's configured for 
both Maven and SBT builds but this guide will be using the Maven example.

Deploying to Kubernetes requires the use of Docker images for each service. 
Chirper leverages [fabric8's docker-maven-plugin](https://dmp.fabric8.io/) to produce Docker images as part of
its build process.

Kubernetes deployments also require the creation of various resources. Inside the `deploy/k8s` directory you'll find 
the resources that are required to deploy the system. They'll be covered in detail below.

### Kubernetes Resources

Since Chirper uses many advanced features like Akka clustering, service discovery, ingress routing and more, support on
Kubernetes uses many different types of resources. Reference the following to discover what kinds of resources are 
used and why they're necessary.

##### StatefulSet

To bootstrap the Akka cluster, Chirper and this guide make use of 
[StatefulSets](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/#objectives). Pods that 
are part of a StatefulSet have a sticky, unique identity that lends itself well to setting up the Akka cluster. 
To do this, the first node is used as seed node and referenced by environment variables. 

##### Service

... words on what a [Service]() is and how it's used in Chirper

##### Ingress

... words on what an [Ingress]() is and how it's used in Chirper

##### Deployment

... words on what a [Deployment]() is and how it's used in Chirper

### Installation script

*Below, the install script in the Chirper repository is dissected. You can find it in its
entirety at `deploy/k8s/minikube/scripts/install.sh`*

Now that all the resources required for deployment have been covered, this guide will cover how to automate the process
of deploying them to Kubernetes.

Deploying Chirper requires the following actions:

* Start up your Minikube
* Deploy Cassandra
* Build Chirper Docker images
* Deploy Chirper
* Deploy nginx
* Open the service in your browser

Let's take a look at how these tasks can be scripted by analyzing the `install.sh`.

#### Starting up your Minikube

For simplicity's sake, it's nice to start from a fresh Minikube installation. The following resets the state of the local
Minikube and configures the environment variables of your terminal session to point Docker to it.
 
```bash
(minikube delete || true) &>/dev/null
minikube start --memory 8192
eval $(minikube docker-env)
```

#### Deploy Cassandra

To deploy Cassandra to Kubernetes, the requisite resources must be created. 
These can be found in `deploy/k8s/minikube/cassandra`.

```bash
# Create the resources
kubectl create -f deploy/k8s/minikube/cassandra

# Wait until it's running
while ! kubectl get pods --selector=app=cassandra | grep 1/1.*Running &>/dev/null; do sleep 1; done

# Inspect its status
kubectl exec cassandra-0 -- nodetool status
```

#### Build Chirper Docker images

Applications must be packaged as Docker images to be deployed to Kubernetes. By using 
[fabric8's docker-maven-plugin](https://dmp.fabric8.io/), these images will be built and published to the Minikube
repository.

```bash
mvn clean package docker:build
docker images
```

#### Deploy Chirper

Next, create the resources required for running Chirper.

```bash
kubectl create -f deploy/k8s/minikube/chirper
```

#### Deploy nginx

Now that Chirper has been deployed, deploy the Ingress resouces and nginx to load the application.

```bash
kubectl create -f deploy/k8s/minikube/nginx
```

#### Open the service in your browser

Everything should be loading - look at the pods with the following command:

```
kubectl get pods
```

```
NAME                                        READY     STATUS    RESTARTS   AGE
activityservice-0                           1/1       Running   0          28m
cassandra-0                                 1/1       Running   0          30m
chirpservice-0                              1/1       Running   0          28m
friendservice-0                             1/1       Running   0          28m
nginx-default-backend-1298687872-vmdpg      1/1       Running   0          28m
nginx-ingress-controller-4020060155-f1dd9   1/1       Running   0          28m
web-0                                       1/1       Running   0          28m
```

Lastly, open up Chirper in the browser:

```
minikube service nginx-ingress
```

## Conclusion

Kubernetes provides many features that complement running a microservices in production. By leveraging the [StatefulSet](),
[Ingress](), and [Service]() resources a Lagom-based microservices system can easily be deployed into your cluster.
[Chirper](https://github.com/lagom/activator-lagom-java-chirper) can be referenced by any developer wishing to
deploy his or her Lagom or Akka cluster to Kubernetes.