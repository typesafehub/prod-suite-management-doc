# Deploying Lagom Microservices on Kubernetes

[Lagom](http://www.lagomframework.com/) is an opinionated microservices framework that makes it quick and easy to
build, test, and deploy your systems with confidence. [Kubernetes](https://kubernetes.io/), an open-source solution
for container orchestration, provides features that complement running Lagom applications in production. This guide
will cover the configuration required to run your Lagom-based system on Kubernetes, taking advantage of many of its
standard features.

## The Challenge

You've created a brand new microservices system using [Lagom](http://www.lagomframework.com/). After evaluating all of 
your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it 
provides for automated deployment, scaling, and management of containerized applications. This raises several 
challenges, however:
 
* Lagom's [Persistent Entity API](https://www.lagomframework.com/documentation/1.3.x/java/PersistentEntityCassandra.html)
leverages [Akka Cluster](http://doc.akka.io/docs/akka/2.5.3/scala/common/cluster.html) and this has its own set of
considerations when deploying to Kubernetes.
* Lagom applications make use of a [Service Locator](https://www.lagomframework.com/documentation/1.3.x/java/ServiceLocator.html)
that must tie in with the facilities that Kubernetes provides.
* Running an application on Kubernetes requires containerization and Lagom systems, being composed of many microservices,
will require many [Docker](https://www.docker.com/) images to be created.

## The Solution

This guide covers the steps required to deploy a Lagom microservices system to Kubernetes. It provides an overview on
the strategy for deploying to a Kubernetes cluster and then dives into the commands and configuration required. It 
specifically covers deploying to your local Kubernetes cluster, by way of 
[Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/), deploying to [IBM Bluemix](https://www.ibm.com/cloud-computing/bluemix/), 
a cloud platform as a service (PaaS) built on Kubernetes, as well as [IBM Bluemix Private Cloud](https://www.ibm.com/us-en/marketplace/private-cloud-as-a-service),
an on-prem Bluemix deployment. Other Kubernetes environments can be used with minimal adjustment.

#### The Setup

This guide demonstrates the solution using the [Chirper](https://github.com/lagom/activator-lagom-java-chirper) Lagom
example app. Before continuing, make sure you have the following installed and configured on your local
machine:

* JDK8+
* [Maven](https://maven.apache.org/) or [sbt](http://www.scala-sbt.org/)
* [Docker](https://www.docker.com/)
* Access to a Kubernetes environment with connectivity to it via the `kubectl` command line tool.
* A clone of the [Lagom Chirper repository](https://github.com/lagom/activator-lagom-java-chirper)

#### About Chirper

Chirper is a Lagom-based microservices system that aims to simulate a Twitter-like website. It's configured for 
both Maven and sbt builds, and this guide will demonstrate how artifacts built using both build tools are deployed to
Kubernetes. Chirper has already been configured for deployment on Kubernetes. The guides below detail this configuration
so that you can emulate it in your own project.

#### Kubernetes Resources

Since Chirper uses many advanced features like Akka clustering, service discovery, ingress routing and more, deploying on
Kubernetes uses many different types of resources. Reference the following to discover what kinds of resources are 
used and why they're necessary.

| Resource                                                                                                | Purpose                                                                                                                                                                                                                                                                                                                                                                           |
|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/)                                          | The basic unit of execution in Kubernetes. A Pod includes one or more co-located and co-scheduled containers. While Chirper doesn't use Pods directly, the Pods are being created through the use of other resource such as `StatefulSet`.                                                                                                                                                                                                             |
| [StatefulSet](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/#objectives) | A controller that provides a unique identity to a set of Pods. This guide will cover how Chirper uses `StatefulSet` to bootstrap its Akka Clusters with a seed node referenced by environment variables. Chirper defines `StatefulSet` resources for each of its services: `friendservice`, `activityservice`, `chirpservice`, and `web`.                                         |
| [Service](https://kubernetes.io/docs/concepts/services-networking/service/)                             | Provides the means to expose TCP and UDP ports to other Pods within the Kubernetes cluster, and it integrates with DNS so they can be discovered via DNS SRV.                                                                                                                                                                                                                     |
| [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)                             | A collection of rules that allow external traffic to reach services running inside Kubernetes. This enables, for example, requests to /api/users to be routed to the friendservice while requests for / are routed to web. It also provides a central place to terminate TLS. In this example, Chirper is configured to use NGINX as the ingress controller and to terminate TLS. |

_Refer to Chirper's resources at `deploy/kubernetes/resources` for more details._

#### Service Location

Lagom makes use of a [Service Locator](https://www.lagomframework.com/documentation/1.3.x/java/ServiceLocator.html) to
call other services within the system. Chirper is configured to use the [service-locator-dns](https://github.com/typesafehub/service-locator-dns)
project to provide a service locator that takes advantage of Kubernetes [Service Discovery](https://kubernetes.io/docs/concepts/services-networking/service/#discovering-services).

Because the names of your service locators will not exactly match the DNS SRV address, `service-locator-dns` has
the ability to translate addresses. Chirper uses this feature to ensure, for example, a service lookup for `friendservice`
will be translated into a DNS SRV lookup for `_http-lagom-api._tcp.friendservice.default.svc.cluster.local`. Chirper
is configured with the following in each of its service's `application.conf`:

```
service-locator-dns {
  name-translators = [
    {
      "^_.+$" = "$0",
      "^.*$" = "_http-lagom-api._tcp.$0.default.svc.cluster.local"
    }
  ]

  srv-translators = [
    {
      "^_http-lagom-api[.]_tcp[.](.+)$" = "_http-lagom-api._http.$1",
      "^.*$" = "$0"
    }
  ]
}
```

_Refer to the various `application.conf` files in the Chirper repository for more details._

## Manual Deployment

Now that all the resources required for deployment have been described, this guide will cover how to automate the process
of deploying them to Kubernetes.

Deploying Chirper requires the following actions:

1. Setup Kubernetes
2. Deploy Cassandra
3. Build Chirper Docker images
4. Deploy Chirper
5. Deploy NGINX
6. Verify Deployment

Let's take a look at how these tasks can be performed from your own terminal. Make sure 
you've `cd`'d into your clone of the Chirper repository before proceeding.

## 1. Setting up your Kubernetes Cluster

You can deploy Chirper to any number of Kubernetes environments. Below, you'll find information on how to do
this on your own local cluster, Minikube, as well as IBM's Bluemix. If you have access to a different
Kubernetes environment, ensure that you've setup `kubectl` and `docker` to point at your cluster and
docker registry. The sections below offer some information on getting both of these environments
setup.

-------------------------

###### Minikube

[Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) provides a way for you to
run a local Kubernetes cluster. The command below will reset your Minikube and ensure 
that `kubectl` and `docker` can communicate with it.

```bash
$ (minikube delete || true) &>/dev/null && \
  minikube start --memory 8192 && \
  eval $(minikube docker-env)
```

###### IBM Bluemix

[IBM Bluemix](https://www.ibm.com/cloud-computing/bluemix/) offers Kubernetes clusters that can be used in production 
environments. To use your Bluemix cluster, follow the instructions on their website. The [IBM Bluemix](https://console.bluemix.net)
console will guide you through creating a cluster, installing the `bx` tool, and using that to
configure `kubectl`.

You'll then need to setup the Container Registry. Consult the [Getting started](https://console.bluemix.net/docs/services/Registry/index.html)
guide for more details.

###### IBM Bluemix Private Cloud

[IBM Bluemix Private Cloud](https://www.ibm.com/cloud-computing/bluemix/) is an on-prem deployment of IBM Bluemix.
To deploy to your Bluemix Private Cloud cluster, you'll need a working deployment of IBM Bluemix Private Cloud and
access to a Docker Registry.

-------------------------

Once you've configured your environment, you should be able to verify access with the following command:

```bash
$ kubectl get nodes
```

## 2. Deploy Cassandra

To deploy Cassandra to Kubernetes, the requisite resources must be created. The command below will create the resources, wait for
Cassandra to start up, and show you its status.


```bash
$ kubectl create -f deploy/kubernetes/resources/cassandra && \
  deploy/kubernetes/scripts/kubectl-wait-for-pods && \
  kubectl exec cassandra-0 -- nodetool status
```

```
service "cassandra" created
statefulset "cassandra" created
Datacenter: DC1-K8Demo
======================
Status=Up/Down
|/ State=Normal/Leaving/Joining/Moving
--  Address     Load       Tokens       Owns (effective)  Host ID                               Rack
UN  172.17.0.4  99.45 KiB  32           100.0%            9f5ffc06-ba53-4f7d-8fbb-c4a522ae3ef8  Rack1-K8Demo
```

_Refer to the files in the Chirper repository at `deploy/kubernetes/resources/cassandra` for more details._

## 3. Build Chirper Docker images

Applications must be packaged as Docker images to be deployed to Kubernetes. This can be accomplished
with both sbt and Maven build tools, both covered below.

----------------------------------

###### Maven

By using 
[fabric8's docker-maven-plugin](https://dmp.fabric8.io/), these images will be built and published to the Minikube
repository. The command below will build Chirper and the Docker images using Maven and this plugin.

```bash
$ mvn clean docker:build
```

_Refer to the various `pom.xml` files in the Chirper repository for more details._

###### sbt

By using [sbt native packager](https://github.com/sbt/sbt-native-packager) Chirper is configured
to be able to build Docker images. The command below will build Chirper and the Docker images using
sbt and this plugin.

```bash
$ sbt -DbuildTarget=kubernetes clean docker:publishLocal
```

_Refer to `build.sbt` in the Chirper repository for more details._

----------------------------------

Next, inspect the images that are available. Note that the various Chirper services all have their own image. These will
be deployed to the cluster.
```bash
$ docker images
```

```
REPOSITORY                                             TAG                 IMAGE ID            CREATED              SIZE
chirper/front-end                                      1.0-SNAPSHOT        717a0d320d9b        56 seconds ago       132MB
chirper/front-end                                      latest              717a0d320d9b        56 seconds ago       132MB
chirper/load-test-impl                                 1.0-SNAPSHOT        db537c9eb880        About a minute ago   143MB
chirper/load-test-impl                                 latest              db537c9eb880        About a minute ago   143MB
chirper/activity-stream-impl                           1.0-SNAPSHOT        cef7df4abf64        About a minute ago   143MB
chirper/activity-stream-impl                           latest              cef7df4abf64        About a minute ago   143MB
chirper/chirp-impl                                     1.0-SNAPSHOT        c9f353510b73        About a minute ago   143MB
chirper/chirp-impl                                     latest              c9f353510b73        About a minute ago   143MB
chirper/friend-impl                                    1.0-SNAPSHOT        2c7aa5d29ce8        About a minute ago   143MB
chirper/friend-impl                                    latest              2c7aa5d29ce8        About a minute ago   143MB
openjdk                                                8-jre-alpine        c4f9d77cd2a1        2 weeks ago          81.4MB
gcr.io/google_containers/kubernetes-dashboard-amd64    v1.6.1              71dfe833ce74        8 weeks ago          134MB
gcr.io/google_containers/k8s-dns-sidecar-amd64         1.14.2              7c4034e4ffa4        2 months ago         44.5MB
gcr.io/google_containers/k8s-dns-kube-dns-amd64        1.14.2              ca8759c215c9        2 months ago         52.4MB
gcr.io/google_containers/k8s-dns-dnsmasq-nanny-amd64   1.14.2              e5c335701995        2 months ago         44.8MB
gcr.io/google-containers/kube-addon-manager            v6.4-beta.1         85809f318123        4 months ago         127MB
gcr.io/google-samples/cassandra                        v12                 a4abd0fb26a4        4 months ago         241MB
gcr.io/google_containers/pause-amd64                   3.0                 99e59f495ffa        14 months ago        747kB
```

## 4. Deploy Chirper

To deploy Chirper, the requisite resources must be created. The command below will create the resources, 
wait for all of them to startup, and show you the cluster's pod status.

```bash
$ kubectl create -f deploy/kubernetes/resources/chirper && \
  deploy/kubernetes/scripts/kubectl-wait-for-pods && \
  kubectl get all
```

```
service "activityservice-akka-remoting" created
service "activityservice" created
statefulset "activityservice" created
service "chirpservice-akka-remoting" created
service "chirpservice" created
statefulset "chirpservice" created
service "friendservice-akka-remoting" created
service "friendservice" created
statefulset "friendservice" created
service "web" created
statefulset "web" created
NAME                READY     STATUS    RESTARTS   AGE
activityservice-0   1/1       Running   0          20s
cassandra-0         1/1       Running   0          5m
chirpservice-0      1/1       Running   0          20s
friendservice-0     1/1       Running   0          20s
web-0               1/1       Running   0          20s
```

_Refer to the files in the Chirper repository at `deploy/kubernetes/resources/chirper` for more details._ 

## 5. Deploy NGINX

Now that Chirper has been deployed, deploy the Ingress resouces and NGINX to load the application. The command
below will create these resources, wait for all of them to startup, and show you the cluster's pod status.

```bash
$ kubectl create -f deploy/kubernetes/resources/nginx && \
  deploy/kubernetes/scripts/kubectl-wait-for-pods && \
  kubectl get pods
```

```
ingress "chirper-ingress" created
deployment "nginx-default-backend" created
service "nginx-default-backend" created
deployment "nginx-ingress-controller" created
service "nginx-ingress" created
NAME                                        READY     STATUS    RESTARTS   AGE
activityservice-0                           1/1       Running   0          52s
cassandra-0                                 1/1       Running   0          5m
chirpservice-0                              1/1       Running   0          52s
friendservice-0                             1/1       Running   0          52s
nginx-default-backend-1298687872-bmhdc      1/1       Running   0          21s
nginx-ingress-controller-1705403548-pv36b   1/1       Running   0          21s
web-0                                       1/1       Running   0          52s
```

_Refer to the files in the Chirper repository at `deploy/kubernetes/resources/nginx` for more details._ 

## 6. Verify Your Deployment

Chirper and all of its dependencies are now running in the cluster. Use the following command to determine the URLs
to open in your browser. After registering an account in the Chirper browser tab, you'll be ready to start Chirping!

```bash
$ echo "Chirper UI (HTTP): $(minikube service --url nginx-ingress | head -n 1)" && \
  echo "Chirper UI (HTTPS): $(minikube service --url --https nginx-ingress | tail -n 1)" && \
  echo "Kubernetes Dashboard: $(minikube dashboard --url)"
```

```
# The URLs below will be different on your system. Be sure to
# run the commands above to produce the correct URLs.

Chirper UI (HTTP): http://192.168.99.101:31408
Chirper UI (HTTPS): https://192.168.99.101:30122
Kubernetes Dashboard: http://192.168.99.101:30000
```

_Note that the HTTPS URL is using a self-signed certificate so you will need to accept it to bypass any browser warnings._

## Automated Deployment

This guide has covered the steps required to manually deploy your resources to Kubernetes. In a production setting, 
you'll often wish to automate this. Chirper includes an install script that will take care of creating
the resources for you. You can find it in the Chirper repository at `deploy/kubernetes/scripts/install`. Use it as a 
template for automating your own deployments.

_Note that for both solutions described below, you'll need to ensure your environment is configured for access to a Docker registry (if applicable)
and that `kubectl` has access to your Kubernetes environment._

------------------

###### Deploying using Minikube

For environments that don't use a registry, such as Minikube, simply launch the script to start the
process.

```bash
$ deploy/kubernetes/scripts/install --all --minikube
```

###### Deploying using a Docker registry

For production environments, you'll need to use a Docker registry. The install script takes an optional argument that
specifies the Docker registry to use. When provided, the script pushes your images there and ensures that the
resources point to them. For example, the following can be used to deploy to a registry namespace `my-namespace` 
that has been setup on IBM Bluemix. You'll need to reference the documentation for the registry you choose, but if
running on IBM Bluemix, the [Container Registry](https://console.bluemix.net/docs/services/Registry/index.html) is a
natural fit. For IBM Bluemix Private Cloud deployments, you'll need to configure our own Docker Registry.

```bash
$ deploy/kubernetes/scripts/install --all --registry my-registry.com/my-namespace
```
-----------------


## Conclusion

Kubernetes provides many features that complement running a microservices in production. By leveraging the 
[StatefulSet](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/#objectives),
[Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/), and 
[Service](https://kubernetes.io/docs/concepts/services-networking/service/) resources a Lagom-based microservices 
system can easily be deployed into your Kubernetes cluster.
The [service-locator-dns](https://github.com/typesafehub/service-locator-dns) project can be used to integrate with
Kubernetes Service Discovery. Maven users can use [fabric8's docker-maven-plugin](https://dmp.fabric8.io/) to
containerize their applications, and sbt users can do the same by employing [sbt native packager](https://github.com/sbt/sbt-native-packager).
[Chirper](https://github.com/lagom/activator-lagom-java-chirper) can be referenced by any developer wishing to
deploy his or her Lagom or Akka cluster to Kubernetes. It's the perfect example for learning
how to deploy your microservices system into Kubernetes and take advantage of its
advanced features like Ingress TLS termination, service location, and more!
