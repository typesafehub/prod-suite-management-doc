# Hello

Starting MiniKube

```
(minikube delete || true) &>/dev/null && \
minikube start --memory 2458 && \
eval $(minikube docker-env)

```

Building Docker images 

```
 sbt clean docker:publishLocal && mvn -f liberty/pom.xml clean package install && docker build -t liberty-app liberty
```

Creating Pods

```
kubectl create -f deploy/resources/lagom && kubectl create -f deploy/resources/nginx && kubectl create -f deploy/resources/liberty
```

Listing Services

```
minikube service list

Sample Output : 

$ minikube service list
|-------------|-----------------------|--------------------------------|
|  NAMESPACE  |         NAME          |              URL               |
|-------------|-----------------------|--------------------------------|
| default     | kubernetes            | No node port                   |
| default     | lagomservice          | No node port                   |
| default     | libertyservice        | No node port                   |
| default     | nginx-default-backend | No node port                   |
| default     | nginx-ingress         | http://192.168.99.105:31627    |
|             |                       | http://192.168.99.105:30803    |
| kube-system | kube-dns              | No node port                   |
| kube-system | kubernetes-dashboard  | http://192.168.99.105:30000    |
|-------------|-----------------------|--------------------------------|

```

Hit http://192.168.99.105:31627/lagom/toLiberty to see how lagom can talk to a liberty app.

Hit http://192.168.99.105:31627/sample.servlet/example to see how liberty can talk to lagom.

