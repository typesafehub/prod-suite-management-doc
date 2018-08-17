lazy val root = project
  .in(file("."))
  .aggregate(
    dcosMicroservices,
    lagomKubernetesK8sDeployMicroservices,
    lagomDcosMarathonDeployMicroservices,
    akkaClusterKubernetesK8sDeploy,
    lagomLibertyWebsphere,
    lagomSbrConductr,
    docs
  )

lazy val dcosMicroservices = project
  .in(file("guides/dcos-microservices"))
  .enablePlugins(ParadoxPlugin)

lazy val lagomKubernetesK8sDeployMicroservices = project
  .in(file("guides/lagom-kubernetes-k8s-deploy-microservices"))
  .enablePlugins(ParadoxPlugin)

lazy val lagomDcosMarathonDeployMicroservices = project
  .in(file("guides/lagom-dcos-marathon-deploy-microservices"))
  .enablePlugins(ParadoxPlugin)

lazy val akkaClusterKubernetesK8sDeploy = project
  .in(file("guides/akka-cluster-kubernetes-k8s-deploy"))
  .enablePlugins(ParadoxPlugin)

lazy val lagomLibertyWebsphere = project
  .in(file("guides/lagom-liberty-websphere"))
  .enablePlugins(ParadoxPlugin)

lazy val lagomSbrConductr = project
  .in(file("guides/lagom-sbr-conductr"))
  .enablePlugins(ParadoxPlugin)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)

name := "prod-suite-management-doc"
