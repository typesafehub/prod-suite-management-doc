lazy val root = project
  .in(file("."))
  .aggregate(
    dcosMicroservices,
    k8sMicroservices,
    docs
  )
    
lazy val dcosMicroservices = project
  .in(file("guides/dcos-microservices"))
  .enablePlugins(ParadoxPlugin)

lazy val k8sMicroservices = project
  .in(file("guides/k8s-microservices"))
  .enablePlugins(ParadoxPlugin)
  
lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)

name := "prod-suite-management-doc"