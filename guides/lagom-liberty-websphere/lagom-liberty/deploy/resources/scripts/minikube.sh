#!/usr/bin/env bash

(minikube delete || true) &>/dev/null && minikube start --memory 2048 && eval $(minikube docker-env)

# clone the repo
# git clone git@github.com:typesafehub/prod-suite-management-doc.git

# cd guides/lagom-liberty-websphere/lagom-liberty

# at this point you should be in the guides/lagom-liberty-websphere/lagom-liberty directory
sbt clean docker:publishLocal && mvn -f liberty/pom.xml clean package install && docker build -t liberty-app liberty

kubectl create -f deploy/resources/lagom && kubectl create -f deploy/resources/nginx && kubectl create -f deploy/resources/liberty

echo "Endpoint Urls: $(minikube service --url nginx-ingress | head -n 1)"
echo "Kubernetes Dashboard: $(minikube dashboard --url)"

# move up the directories once finished
# cd ../../..