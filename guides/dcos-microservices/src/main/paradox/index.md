# How to deploy microservices to DC/OS

## The problem

You've now created your beautiful microservice system using [Lagom](http://www.lagomframework.com/) and you now need to deploy it in its entirety to DC/OS. There are many moving parts to a microservices system including service discovery and event sourcing; both of which assume a distributed system. Lagom is based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/) and they are Highly Available (HA) by default thereby requiring clustering. So, how then do you deploy these inherently complex systems to DC/OS?