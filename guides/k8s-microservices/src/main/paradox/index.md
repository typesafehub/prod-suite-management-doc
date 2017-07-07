# How to deploy microservices to Kubernetes

## The problem

You've created a brand new microservices system using [Lagom](http://www.lagomframework.com/). After evaluating all of 
your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it 
provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: 
since Lagom is based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/) 
and both are Highly Available (HA) by default, they require clustering. Lagom applications also need the ability to
locate other services running in its environment. Finally, running an application on Kubernetes requires 
containerization. How exactly do we deploy these systems to Kubernetes?

## The solution

In this guide, we'll cover the steps required to deploy a Lagom microservices system to your local Kubernetes cluster by
way of [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/). We'll also touch on the modifications
required to this recipe to run on [IBM BlueMix](), a cloud platform as a service (PaaS) built on Kubernetes.

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

We'll be covering the resources required to run a Lagom system on Kubernetes. This will detail the various resource 
types and their purpose, as well as how they need to be declared.

### About Chirper

Chirper is a Lagom-based microservices system that aims to simulate a Twitter-like website. It's configured for 
both Maven and SBT builds but for this guide we'll be using the Maven example.

Deploying to Kubernetes requires the use of Docker images for each service. 
Chirper leverages [fabric8's docker-maven-plugin](https://dmp.fabric8.io/) to produce Docker images as part of
its build process.

Kubernetes deployments also require the creation of various resources. Inside the `deploy/k8s` directory you'll find 
the resources that are required to deploy the system. We'll  cover these in detail below.

### Resources



 ... words ...

### Installation script

*Below, we're dissecting the various parts of install script in the Chirper repository. You can find it in its
entirety at `deploy/k8s/minikube/scripts/install.sh`*

Now that we've covered all the resources required for deployment, we'll want to cover how we've automated this
process by building an installation script.

Deploying Chirper requires the following actions:

* Start up your Minikube
* Deploy Cassandra
* Build Chirper Docker images
* Deploy Chirper
* Deploy nginx
* Open the service in your browser

Let's take a look at how we've scripted these tasks by analying the `install.sh`.

#### Starting up your Minikube

For simplicity's sake, we like to start from a fresh Minikube installation. The following resets the state of the local
Minikube and configures the environment variables of your terminal session to point Docker to it.
 
```bash
(minikube delete || true) &>/dev/null
minikube start --memory 8192
eval $(minikube docker-env)
```

#### Deploy Cassandra

To deploy Cassandra to Kubernetes, we need to create the requisite resources which 
we've placed in `deploy/k8s/minikube/cassandra`.

```bash
kubectl create -f deploy/k8s/minikube/cassandra

# TODO some command that waits until cassandra has started

# TODO some command that shows cassandra is working
```

#### Build Chirper Docker images

Applications must be packaged as Docker images to be deployed to Kubernetes. We'll build all of Chirper's images as 
Docker images that are published to the Minikube's local repository -- 
thanks to the `eval $(minikube docker-env)` statement covered above! 

```bash
mvn clean package docker:build
docker ps
```

#### Deploy Chirper

Next, we'll need to create the resources required for running Chirper.

```bash
kubectl create -f deploy/k8s/minikube/chirper
```

#### Deploy nginx

Now that Chirper has been deployed, we'll need to deploy the Ingress resouces and nginx to load the application.

```bash
kubectl create -f deploy/k8s/minikube/nginx
```

#### Open the service in your browser

Everything should be loading - let's look at the pods with the following command:

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

Lastly, let's open up Chirper in the browser:

```
minikube service nginx-ingress
```

## Conclusion

... words ...
