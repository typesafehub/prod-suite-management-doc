# Service Discovery between Lagom and WebSphere Liberty Apps 

<style type="text/css">
  pre.code-bash::before {
    content: '$ ';
    color: #009900;
    font-weight: bold;
  }
</style>

[Lagom](http://www.lagomframework.com/) is an opinionated microservices framework that makes it quick and easy to
build, test, and deploy your systems with confidence. [WebSphere Liberty](https://developer.ibm.com/wasdev/websphere-liberty/), is a fast, dynamic, easy-to-use Java EE application server. 

## The Challenge
In a heterogeneous distributed system it is often necessary to interface with an existing codebase while trying to write new applications in different frameworks. This guide will cover the basic configuration required to enable interactions between your Lagom-based system and a simple servlet based application running on WebSphere deployed on Kubernetes. The key outcome of this guide is to understand one of the ways for lagom applications to discover and interact with heterogeneous applications and vice-versa.

## Setup

We will deploy a Lagom application and WebSphere Application to Kubernetes. Both the applications will be containerized using docker.
The Lagom application is written in Scala. The WebSphere application uses Java and Servlets. We will show simple interactions between the applications by invoking restful endpoints in these applications. We will also show the steps involved in building this setup.

Briefly, we will perform the following steps:

* Establishing a Kubernetes cluster.
* Lagom systems will require [Docker](https://www.docker.com/) images to be created.
* A WebSphere Liberty based application will also be dockerized before deploying to Kubernetes.
* Apply a DNS [Service Locator](https://www.lagomframework.com/documentation/1.3.x/java/ServiceLocator.html) for discovering services on Kubernetes.

## The Solution

This guide covers the steps required to deploy Lagom and WebSphere Liberty applications to Kubernetes. It provides an overview of
the strategy for deploying to a Kubernetes cluster. It specifically covers deploying to your local Kubernetes cluster, by way of [Minikube](https://Kubernetes.io/docs/getting-started-guides/minikube/).

#### The Setup

This guide demonstrates the solution using the [lagom-liberty](https://github.com/typesafehub/prod-suite-management-doc/tree/master/guides/lagom-liberty-websphere/lagom-liberty) Lagom
example application. Before continuing, make sure you have the following installed and configured on your local
machine:

* JDK8+
* [Maven](https://maven.apache.org/) and [sbt](http://www.scala-sbt.org/)
* [Docker](https://www.docker.com/)
* Access to a Kubernetes environment with connectivity to it via the `kubectl` command line tool.
* A clone of the [lagom-liberty](https://github.com/typesafehub/prod-suite-management-doc/tree/master/guides/lagom-liberty-websphere/lagom-liberty)

#### Liberty and Lagom Application
Once you clone [lagom-liberty](https://github.com/typesafehub/prod-suite-management-doc), you will see two applications 1) Liberty; and 2) Lagom within the `guides/lagom-liberty-websphere/lagom-liberty` directory.
The original source of the liberty application is the [WASDev:Servlet](https://github.com/WASdev/sample.servlet). 
This application has a servlet which simply makes a HTTP call to the lagom api and processes the response.
The lagom application has a service binding to tie the liberty service to the lagom application. There is additional configuration required so that the services can discover each other. We will go through this aspect in the next section.

#### Service Discovery

Lagom makes use of a [Service Locator](https://www.lagomframework.com/documentation/1.3.x/java/ServiceLocator.html) to
call other services within the system. The Lagom application is configured to use the [scala-service-locator-dns](https://github.com/typesafehub/service-locator-dns) project to provide a service locator that takes advantage of Kubernetes [Service Discovery](https://kubernetes.io/docs/concepts/services-networking/service/#discovering-services). It is also available for Java [scala-service-locator-dns](https://github.com/typesafehub/service-locator-dns).

Because the names of the service locators will not exactly match the [DNS SRV](https://en.wikipedia.org/wiki/SRV_record) address, `service-locator-dns` has
the ability to translate addresses. The Lagom application uses this feature to ensure, for example, a service lookup for `libertyservice`
will be translated into a DNS SRV lookup for `_liberty-api._tcp.libertyservice.default.svc.cluster.local`. The Lagom application
is configured with the following configuration in `application.conf`:

@@snip [application.conf] (../../../lagom-liberty/lagom-impl/src/main/resources/application.conf) { #lagom-dns-config }

Notice, the name of the service (`libertyservice`) and the name of the port(`liberty-api`) that we have used in the configuration. These names must match the names in the Kubernetes resource files, otherwise the service discovery will fail.

> Refer to the various `application.conf` files in the Lagom-Liberty repository for more detail on the DNS SRV configuration.

## Deployment

Now that all the resources required for deployment have been described, this guide will cover how to automate the process
of deploying them to Kubernetes. Deploying our services requires the following actions:

1. Setup Kubernetes
2. Build and Deploy Lagom Application
3. Build and Deploy Liberty Application
4. Verify Deployment

Let's take a look at how these tasks can be performed from your own terminal. Make sure 
you've `cd`'d into your clone of the Lagom-Liberty repository before proceeding.

## 1. Setting up your Kubernetes Cluster

You can deploy Lagom-Liberty sample to any number of Kubernetes environments. Below, you'll find information on how to do
this on your own local cluster, Minikube, as well as IBM's Bluemix. If you have access to a different
Kubernetes environment, ensure that you've setup `kubectl` and `docker` to point at your cluster and
docker registry. The sections below offer some information on getting both of these environments
setup.

-------------------------

###### Minikube

[Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) provides a way for you to
run a local Kubernetes cluster. The command below will reset your Minikube and ensure 
that `kubectl` and `docker` can communicate with it.

> Note that the following commands will reset any existing Minikube session.

<pre class="code-bash prettyprint prettyprinted">
(minikube delete || true) &>/dev/null && \
minikube start --memory 2048 && \
eval $(minikube docker-env)
</pre>

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

###### Verify Kubernetes Cluster Setup

Once you've configured your environment, you should be able to verify access with the following command:

<pre class="code-bash prettyprint prettyprinted">
kubectl get nodes
</pre>

-------------------------

## 2. Build Application Images

### 2.1 Build the Lagom Application Image

Applications must be packaged as Docker images to be deployed to Kubernetes. This can be accomplished
with both sbt and Maven build tools, with sbt covered below.

> For general assistance on setting up your Lagom build please refer to ["Defining a Lagom Build" in the Lagom documentation](https://www.lagomframework.com/documentation/1.3.x/scala/LagomBuild.html).

By using [sbt native packager](https://github.com/sbt/sbt-native-packager) Lagom-Liberty is configured
to be able to build Docker images. The command below will build our service and the Docker images using
sbt and this plugin.

<pre class="code-bash prettyprint prettyprinted">
sbt clean docker:publishLocal
</pre>

> Refer to `build.sbt` in the [Lagom-Liberty Repository](https://github.com/typesafehub/prod-suite-management-doc/tree/master/guides/lagom-liberty-websphere/lagom-liberty) for more details.

### 2.2 Build the WebSphere Liberty Application Image
We will build the WebSphere liberty docker image by using the base docker image available on the docker hub [WebSphere Liberty Docker] (https://docs.docker.com/samples/library/websphere-liberty/). The docker image will contain the war file built using [Maven](https://maven.apache.org/). 
The following command can be run from the base  directory to achieve this. You can also refer to the dockerfile in the liberty directory for completeness.
 
<pre class="code-bash prettyprint prettyprinted">
mvn -f liberty/pom.xml clean package install && docker build -t liberty-app liberty
</pre>

### 2.3 Verify Images

To check that the docker images are created successfully, you can use the `docker images` command.

```bash

$ docker images
REPOSITORY                                             TAG                 IMAGE ID            CREATED             SIZE
liberty-app                                            latest              8781ae4e3da2        3 minutes ago       394MB
lagom/lagom-impl                                       1.0-SNAPSHOT        8343c6c1e4b4        4 minutes ago       827MB
lagom/lagom-impl                                       latest              8343c6c1e4b4        4 minutes ago       827MB
websphere-liberty                                      webProfile7         20ea1675ecd6        2 weeks ago         392MB
openjdk                                                latest              4551430cfe80        4 weeks ago         738MB
gcr.io/google_containers/k8s-dns-sidecar-amd64         1.14.4              38bac66034a6        2 months ago        41.8MB
gcr.io/google_containers/k8s-dns-kube-dns-amd64        1.14.4              a8e00546bcf3        2 months ago        49.4MB
gcr.io/google_containers/k8s-dns-dnsmasq-nanny-amd64   1.14.4              f7f45b9cb733        2 months ago        41.4MB
gcr.io/google-containers/kube-addon-manager            v6.4-beta.2         0a951668696f        2 months ago        79.2MB
gcr.io/google_containers/kubernetes-dashboard-amd64    v1.6.1              71dfe833ce74        3 months ago        134MB
gcr.io/google_containers/pause-amd64                   3.0                 99e59f495ffa        15 months ago       747kB


```

## 3. Deploy Lagom and WebSphere Liberty Application

After Step 3, we have the docker images which can be deployed to Kubernetes. The Lagom-Liberty repository has the Kubernetes resources which we will use to create the stateful-sets for Lagom and Liberty application. We will also use Nginx for ingress. 

<pre class="code-bash prettyprint prettyprinted">
kubectl create -f deploy/resources/lagom && \
  kubectl create -f deploy/resources/nginx && \
  kubectl create -f deploy/resources/liberty
</pre>

...which then yields the following output:

```bash
service "lagomservice" created
statefulset "lagomservice" created
ingress "lagom-ingress" created
ingress "liberty-ingress" created
deployment "nginx-default-backend" created
service "nginx-default-backend" created
deployment "nginx-ingress-controller" created
service "nginx-ingress" created
service "libertyservice" created
statefulset "libertyservice" created

```

You can also look at the status of all the Kubernetes pods and services by doing get all.

```bash
$ kubectl get all
NAME                                          READY     STATUS    RESTARTS   AGE
po/lagomservice-0                             1/1       Running   0          18s
po/libertyservice-0                           1/1       Running   0          17s
po/nginx-default-backend-1866436208-g1fbx     1/1       Running   0          18s
po/nginx-ingress-controller-667491271-xtrhn   0/1       Running   0          18s

NAME                        CLUSTER-IP   EXTERNAL-IP   PORT(S)                      AGE
svc/kubernetes              10.0.0.1     <none>        443/TCP                      33m
svc/lagomservice            None         <none>        9000/TCP                     18s
svc/libertyservice          None         <none>        9080/TCP                     17s
svc/nginx-default-backend   10.0.0.249   <none>        80/TCP                       18s
svc/nginx-ingress           10.0.0.119   <pending>     80:30425/TCP,443:31034/TCP   17s

NAME                          DESIRED   CURRENT   AGE
statefulsets/lagomservice     1         1         18s
statefulsets/libertyservice   1         1         17s

NAME                              DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
deploy/nginx-default-backend      1         1         1            1           18s
deploy/nginx-ingress-controller   1         1         1            0           18s

NAME                                    DESIRED   CURRENT   READY     AGE
rs/nginx-default-backend-1866436208     1         1         1         18s
rs/nginx-ingress-controller-667491271   1         1         0         18s
```
You can inspect the resource file in the Lagom-Liberty repository under the deploy folder.

> Refer to the files in the Lagom-Liberty repository at `deploy/resources` for more details.

Next, we will describe the important steps we took so that applications are discoverable to each other.

## 4. Enabling Service Discovery
Service discovery in a Kubernetes based environment has to follow the Kubernetes convention for dns records. 
You can read more about these here [Kubernetes-DNS]("https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/").


#### Lagom to Liberty
We will use the [ServiceLocator-DNS] ("https://github.com/typesafehub/service-locator-dns/") library. In development mode it is sufficient to map the service name (`libertyservice`) to any url.
The `LagomLoader` has been modified to use the `DNSServiceLocator`. Lagom will automatically try and use service-locator-dns. The following code demonstrates how we can configure the Lagom application to use the Service Locator DNS library. We will demonstrate the use of Scala API, there is a java api available as well.

@@snip [LagomLoader.scala] (../../../lagom-liberty/lagom-impl/src/main/scala/com/lightbend/lagom/impl/LagomLoader.scala) { #application-loader }

In dev mode the service url will be picked up from build.sbt. In this example it is mapped to port 9080 which is the default port for the WebSphere Liberty server.

@@snip [LagomLoader.scala] (../../../lagom-liberty/build.sbt) { #local-liberty }

#### Liberty to Lagom
The Liberty application will discover the Lagom application using the combination of environment variables and DNS SRV translation.
The environment variables are set by the `LibertyStateful` set resource.

```
{
  "name": "LAGOM_SERVICE",
  "value": "_http-lagom-api._tcp.lagomservice.default.svc.cluster.local"
}
```
We translate this DNS-SRV record using DNS library [Spotify-DNS]("https://github.com/spotify/dns-java"). The following snippet shows how we can use the Spotiy-DNS library to work with the DNS-SRV entry of the Lagom Service. 

@@snip [Example.Servlet] (../../../lagom-liberty/liberty/src/main/java/application/servlet/ExampleServlet.java) { #SERVICE_LOOKUP }

## 5. Understanding the Kubernetes Resources

We have now deployed the Lagom and Liberty applications in the cluster. Next, we will determine the urls we can use to access the applications.
After that, we will list the service to find the Kubernetes dashboard and the `nginx` proxy ingress.

<pre class="code-bash prettyprint prettyprinted">
minikube service list

|-------------|-----------------------|--------------------------------|
|  NAMESPACE  |         NAME          |              URL               |
|-------------|-----------------------|--------------------------------|
| default     | kubernetes            | No node port                   |
| default     | lagomservice          | No node port                   |
| default     | libertyservice        | No node port                   |
| default     | nginx-default-backend | No node port                   |
| default     | nginx-ingress         | http://192.168.99.100:30425    |
|             |                       | http://192.168.99.100:31034    |
| kube-system | kube-dns              | No node port                   |
| kube-system | kubernetes-dashboard  | http://192.168.99.100:30000    |
|-------------|-----------------------|--------------------------------|
</pre>


If we access the dashboard at http://192.168.99.100:30000 then we will be able to see our pods and services.

## 6. Confirming Our Deployments

We will confirm our deployments by hitting the Lagom and Liberty deployments. We will use the nginx ingress urls for this. Hit http://192.168.99.100:30425/lagom/toLiberty (Note that your ip/port will be different.  Use the first nginx-ingress entry from step 5 above) to see lagom talk to the liberty application. You should see the following:

```
External Service Replied with --- 
Served at: /sample.servlet
Hello, from a Servlet!
```

Hit http://192.168.99.100:30425/sample.servlet/example to see liberty talk to lagom. You should see the following:

```
Hello liberty
```

This confirms that our entire setup is working. You should be able to add more routes and play around with the code and see the changes.
Code changes will require new docker images to be built and deployed to Kubernetes cluster.


## 7. Conclusion

We leveraged service discovery of in two different ways to enable interactions between Lagom and WebSphere Liberty based microservices. 
The [service-locator-dns](https://github.com/typesafehub/service-locator-dns) project can be used to integrate Lagom based microservices with Kubernetes Service Discovery. 
We also used environment variables along with [Spotify-DNS]("https://github.com/spotify/dns-java") library to enable J2EE bsaed apps to discover Lagom apps. 

All the commands used in this guide are available under the scripts folder in the [lagom-liberty-repository] ("https://github.com/typesafehub/prod-suite-management-doc/tree/master/guides/lagom-liberty-websphere/lagom-liberty/deploy/resources/scripts").

-------