# How to deploy microservices to DC/OS

## The problem

You've created your beautiful microservice system using [Lagom](http://www.lagomframework.com/) and you now need to deploy it in its entirety to DC/OS. There are many moving parts to a microservices system including service discovery and event sourcing; both of which assume a distributed system. Lagom is based on [Play](https://www.playframework.com/) and [Akka](http://akka.io/) and they are Highly Available (HA) by default, thereby requiring clustering. So, how then do you deploy these necessarily complex systems to DC/OS?

## The solution

What we'll do in this guide is use [ConductR](http://conductr.lightbend.com/) to deploy your Lagom service. ConductR 2.0 can operate either in a stand-alone manner or as a Mesos framework\*. ConductR has been designed from the ground-up with Mesos in mind and represents the easiest way to manage Reactive Platform based applications and services on Mesos platforms, including DC/OS. For example, one thing you do not have to think about managing is establishing Akka cluster systems, and another is service discovery. You'll get both of these important and powerful capabilities taken care of for you. 

@@@ note
As at the time of writing ConductR 2.0 is available as an Early Access release and can be obtained by [contacting our sales department](https://www.lightbend.com/contact). We will also be offering a free version of ConductR in the near future and will update this guide for when it becomes available. Meanwhile, read on to gain a feel for how straightforward it is to deploy your Lagom system to DC/OS!
@@@

\* _Note that Mesos frameworks are also known as "services" to DC/OS._ 

### Prerequisites

The solution proposed here utilizes [sbt](http://www.scala-sbt.org/) and a sample Lagom system known as "Chirper". The following are prerequisites to the solution:

* an [installation of ConductR on DC/OS](http://conductr.lightbend.com/docs/2.0.x/Install#DC/OS-Installation) with connectivity to it via the `dcos` command line tool; 
* a working instance of Cassandra hosted by DC/OS;
* having installed the [ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-lightbend-conductr) and run the `conduct setup-dcos` command in order to be able to use `dcos conduct` commands; and
* the cloning of the [Lagom Chirper repository](https://github.com/lagom/activator-lagom-java-chirper).

### Our approach

We will generate an installation script representing our deployment and then tailor it for DC/OS. This will include:

* removing the Cassandra deployment from the installation script given that DC/OS will be hosting Cassandra; and
* generating a bundle configuration that will advise each Lagom service on where to locate the DC/OS hosted Cassandra.

### Generate an installation script

Chirper comes with [sbt-conductr](https://github.com/typesafehub/sbt-conductr#sbt-conductr) integration included for your convenience. To generate a script:

```
$ sbt 
> generateInstallationScript
[info] Updating {file:/Users/huntc/Projects/typesafe/activator-lagom-java-chirper/}friend-api...
[info] Cassandra bundle configuration has been created: /Users/huntc/Projects/typesafe/activator-lagom-java-chirper/target/bundle-configuration/cassandra-configuration-20c7dbcc5bbff26e635610527f4eac27d5fe3350ae42812f5216a9a9bb4600e5.zip
... 

The ConductR installation script has been successfully created at:
  /Users/huntc/Projects/typesafe/activator-lagom-java-chirper/target/install.sh
[success] Total time: 78 s, completed 22/11/2016 4:57:41 PM
```

### Edit the installation script

Here is the beginning and end of a generated installation script prior to any editing:

```
#!/usr/bin/env bash
cd "$( dirname "${BASH_SOURCE[0]}" )"

echo "Deploying friend-impl..."
FRIEND_IMPL_BUNDLE_ID=$(conduct load ../friend-impl/target/bundle/friend-impl-v1-6f481ed52c24ba573d9fc31d9a5e865f6fbe04a3e31df2e0f291d23648c337dc.zip  --long-ids -q)
conduct run ${FRIEND_IMPL_BUNDLE_ID} --no-wait -q
                 
...
                 
echo "Deploying cassandra..."
CASSANDRA_BUNDLE_ID=$(conduct load cassandra bundle-configuration/cassandra-configuration-20c7dbcc5bbff26e635610527f4eac27d5fe3350ae42812f5216a9a9bb4600e5.zip --long-ids -q)
conduct run ${CASSANDRA_BUNDLE_ID} --no-wait -q
                 
echo 'Your system is deployed. Running "conduct info" to observe the cluster.'
conduct info
```

We now need to replace all of the `conduct` commands with `dcos conduct`. This will then allow us to communicate with ConductR on DC/OS. 

We also remove the deployment of Cassandra as that is hosted by DC/OS. Lastly, you can remove the "load-test-impl" service as it is not necessary in order to see the Chirper system run. Our resulting file will be similar to:

```
#!/usr/bin/env bash
cd "$( dirname "${BASH_SOURCE[0]}" )"

echo "Deploying friend-impl..."
FRIEND_IMPL_BUNDLE_ID=$(dcos conduct load ../friend-impl/target/bundle/friend-impl-v1-6f481ed52c24ba573d9fc31d9a5e865f6fbe04a3e31df2e0f291d23648c337dc.zip  --long-ids -q)
dcos conduct run ${FRIEND_IMPL_BUNDLE_ID} --no-wait -q
      
... 

echo 'Your system is deployed. Running "dcos conduct info" to observe the cluster.'
dcos conduct info
```

We are not finished though - we must first provide some configuration for each service that will declare how to locate Cassandra on DC/OS. This is described next...
 
### Generate a bundle configuration

Create a folder named `dcos-cassandra-config` and then a `bash` script within that named `runtime-config.sh`. Create the configuration in a place outside of the project so that you can use it again. The contents of `runtime-config.sh` should be just:

```
export CASSANDRA_SERVICE_NAME="_node-0._tcp.cassandra.mesos"
```

The service name of Cassandra can be declared as an environment variable to Lagom. As such we declare it as `_node-0._tcp.cassandra.mesos`, which is the default [DNS SRV](https://en.wikipedia.org/wiki/SRV_record) name of Cassandra when hosted by DC/OS. ConductR will not find this service name within the systems that it manages, and so will fall back on DNS SRV to locate it. The same approach is used for locating Kafka, Elasticsearch and any other service hosted by DC/OS.

We then create a bundle configuration of the directory using ConductR's `shazar` command:

```
$ shazar dcos-cassandra-config/
Created digested ZIP archive at ./dcos-cassandra-config-63cf0c3debc87a6c6f1477764f44f0a5282b8d3caa0986d3477a666362daf7cb.zip
```

Let's now revisit our installation script.

### Loading the bundle configuration

The bundle configuration must now be declared to the `dcos conduct load` commands of the installation script, but only for the Lagom services and not the Play application of Chirper i.e. just:

* friend-impl;
* chirp-impl; and
* activity-stream-impl.

What we want to do is supply the installation script with a the bundle configuration that we generated (`dcos-cassandra-config-63cf0c3debc87a6c6f1477764f44f0a5282b8d3caa0986d3477a666362daf7cb.zip`). Given that my configuration was generated on the desktop, each of the service load commands should  look similar to:

```
FRIEND_IMPL_BUNDLE_ID=$(dcos conduct load ../friend-impl/target/bundle/friend-impl-v1-6f481ed52c24ba573d9fc31d9a5e865f6fbe04a3e31df2e0f291d23648c337dc.zip ~/Desktop/dcos-cassandra-config-63cf0c3debc87a6c6f1477764f44f0a5282b8d3caa0986d3477a666362daf7cb.zip --long-ids -q)
```

Note then that a bundle's configuration is supplied as the second parameter to the `load` sub command.

We should now be ready to install our Lagom system to DC/OS.

### Running the script

To run our installation script:

```
$ activator-lagom-java-chirper/target/install.sh 
Deploying friend-impl...
Deploying chirp-impl...
Deploying activity-stream-impl...
Deploying front-end...
Your system is deployed. Running "dcos conduct info" to observe the cluster.
ID               NAME                  #REP  #STR  #RUN
6f481ed-63cf0c3  friend-impl              1     0     1
7e9ec12-63cf0c3  activity-stream-impl     1     0     1
dc816f0-63cf0c3  chirp-impl               1     0     1
7efd30e-e8a73ae  front-end                1     0     1
412c329-4bcea22  conductr-haproxy         1     0     1

```

## Conclusion

What we've done is created a bash script that can be run repeatedly to install your Lagom system to DC/OS. Akka cluster management is required by Lagom to manage the persistence layer and this, along with automatically locating a DC/OS hosted Cassandra, is taken care of for you.

The installation script can be generalized further of course; it is `bash` after all! Note also that `conduct load` and `conduct run` commands are idempotent, so you can run the script as many times as you want.

This guide shows how a Lagom microservice system can be established on DC/OS. With ConductR's Continuous Delivery mechanism, subsequently publishing your microservice will cause it to be automatically rolled into the cluster with no human intervention.