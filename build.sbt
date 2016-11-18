lazy val root = project
  .in(file("."))
  .aggregate(
    dcosMicroservices,
    docs
  )
    
lazy val dcosMicroservices = project
  .in(file("guides/dcos-microservices"))
  .enablePlugins(ParadoxPlugin)
  
lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)