# How to deploy microservices to Kubernetes

## The problem

You've created a brand new microservices system using [Lagom](http://www.lagomframework.com/). After evaluating all of 
your deployment options, you've chosen to deploy to [Kubernetes](https://kubernetes.io/) to leverage the facilities it 
provides for automated deployment, scaling, and management of containerized applications. This raises a problem though: 
since Lagom is based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/) and leverages Lagom's 
[Persistent Entity API](https://www.lagomframework.com/documentation/1.3.x/java/PersistentEntityCassandra.html), 
Akka cluster is required. Lagom applications also need the ability to locate other services running in its environment.
Finally, running an application on Kubernetes requires containerization. How exactly are these complex systems 
deployed to Kubernetes?

## The solution

In this guide, the steps required to deploy a Lagom microservices system to your local Kubernetes cluster by
way of [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) will be covered. It will also touch on the
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

##### Pod

The basic unit of execution in Kubernetes is a [Pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/). A pod
contains one or more containers that are co-located and co-scheduled. While Chirper doesn't use Pods directly, they
are used by other resource types.

##### StatefulSet

To bootstrap the Akka cluster, Chirper and this guide make use of 
[StatefulSets](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/#objectives). Pods that 
are part of a StatefulSet have a sticky, unique identity that helps bootstrap the Akka cluster. 
To do this, the first node is used as seed node and referenced by environment variables. Chirper defines `StatefulSet`
resources for each of its services: `friendservice`, `activityservice`, `chirpservice`, and `web`

##### Service

A [Service](https://kubernetes.io/docs/concepts/services-networking/service/) provides the means to expose TCP and
UDP ports to other pods within the cluster.

Chirper requires TCP ports to be exposed for Akka remoting. This is done using a `Service` declaration. The exposed 
port will be used by `StatefulSet` declarations to bootstrap the Akka cluster.

Chirper also exposes various HTTP endpoints from the microservices defined within the Chirper itself. The TCP port 
for each service is also exposed using `Service` declaration. The name `http-lagom-api` is used as the name
thereby allowing these endpoints to be looked up via Kubernetes DNS using DNS SRV lookup.

##### Ingress

An [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) is a collection of rules that 
allow external traffic to reach services running inside Kubernetes. This allows, for example, 
requests to `/api/users` to be routed to the `friendservice` while requests for `/` are routed to `web`. It also gives 
you a central place to terminate TLS and Chirper is configured to do this for you.

Chirper is configured to use nginx as the ingress controller.

### Installation script

_Below, the install script in the Chirper repository is dissected. You can find it in its
entirety at `deploy/k8s/minikube/scripts/install`_

Now that all the resources required for deployment have been described, this guide will cover how to automate the process
of deploying them to Kubernetes.

Deploying Chirper requires the following actions:

* Start up your Minikube
* Deploy Cassandra
* Build Chirper Docker images
* Deploy Chirper
* Deploy nginx
* Open the service in your browser

Let's take a look at how these tasks can be scripted by analyzing `install`. Feel free to run these commands
from your own terminal as well. Should you do that, make sure you've `cd`'d into the Chirper repository.

#### Starting up your Minikube

For simplicity's sake, it's nice to start from a fresh Minikube installation. The following resets the state of the local
Minikube and configures the environment variables of your terminal session to point Docker to it.
 
```bash
(minikube delete || true) &>/dev/null && \
minikube start --memory 8192 && \
eval $(minikube docker-env)
```

#### Deploy Cassandra

To deploy Cassandra to Kubernetes, the requisite resources must be created. The command below will create the resources, wait for
Cassandra to start up, and show you its status.


```bash
kubectl create -f deploy/k8s/minikube/cassandra && \
deploy/k8s/minikube/scripts/kubectl-wait-for-pods && \
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

_Refer to the files in the Chirper repository at `deploy/k8s/minikube/cassandra` for more details._

#### Build Chirper Docker images

Applications must be packaged as Docker images to be deployed to Kubernetes. By using 
[fabric8's docker-maven-plugin](https://dmp.fabric8.io/), these images will be built and published to the Minikube
repository. The command below will build Chirper and the Docker images using Maven and this plugin.

```bash
mvn clean package docker:build
```

```
...

[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 01:18 min
[INFO] Finished at: 2017-07-13T11:45:25-05:00
[INFO] Final Memory: 79M/460M
[INFO] ------------------------------------------------------------------------
```

Next, inspect the images that are available. Note that the various Chirper services all have their own image. These will
be deployed to the cluster.
```
docker images
```

```bash
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

_Refer to the various `pom.xml` files in the Chirper repository for more details._

#### Deploy Chirper

To deploy Chirper, the requisite resources must be created. The command below will create the resources, 
wait for all of them to startup, and show you the cluster's pod status.

```bash
kubectl create -f deploy/k8s/minikube/chirper && \
deploy/k8s/minikube/scripts/kubectl-wait-for-pods && \
kubectl get pods
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

_Refer to the files in the Chirper repository at `deploy/k8s/minikube/chirper` for more details._ 

#### Deploy nginx

Now that Chirper has been deployed, deploy the Ingress resouces and nginx to load the application. The command
below will create these resources, wait for all of them to startup, and show you the cluster's pod status.

```bash
kubectl create -f deploy/k8s/minikube/nginx && \
deploy/k8s/minikube/scripts/kubectl-wait-for-pods && \
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

_Refer to the files in the Chirper repository at `deploy/k8s/minikube/nginx` for more details._ 

#### Open the service in your browser

Chirper and all of its dependencies are now running in the cluster. Use the following command to determine the URLs
to open in your browser. After registering an account in the Chirper browser tab, you'll be ready to start Chirping!

```bash
echo "Chirper UI (HTTP): $(minikube service --url nginx-ingress | head -n 1)" && \
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

## Running in Production using IBM BlueMix

_todo - blocked due to dependency on access to IBM's cloud service_

## Conclusion

Kubernetes provides many features that complement running a microservices in production. By leveraging the [StatefulSet](),
[Ingress](), and [Service]() resources a Lagom-based microservices system can easily be deployed into your cluster.
[Chirper](https://github.com/lagom/activator-lagom-java-chirper) can be referenced by any developer wishing to
deploy his or her Lagom or Akka cluster to Kubernetes. It's the perfect example for learning
how to deploy your microservices system into Kubernetes and take advantage of its
advanced features like Ingress TLS termination, service location, and more!