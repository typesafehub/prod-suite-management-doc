Servlet Sample [![Build Status](https://travis-ci.org/WASdev/sample.servlet.svg?branch=master)](https://travis-ci.org/WASdev/sample.servlet)
==============

This project contains a simple Servlet application.

Download [sample.servlet.war](https://jitpack.io/com/github/WASdev/sample.servlet/2.1.0/sample.servlet-2.1.0.war).

## Running in Eclipse

1. Download and install [Eclipse with the WebSphere Developer Tools](https://developer.ibm.com/wasdev/downloads/liberty-profile-using-eclipse/).
2. Create a new Liberty Profile Server. See [step 3](https://developer.ibm.com/wasdev/downloads/liberty-profile-using-eclipse/) for details.
3. Clone this repository.
4. Import the sample into Eclipse using *File -> Import -> Maven -> Existing Maven Projects* option.
5. Right click on the project and go to *Properties > Project Facets* and select *Dynamic Web Module* (if not already selected).
6. Deploy the sample into Liberty server. Right click on the *servlet* sample and select *Run As -> Run on Server* option. Find and select the Liberty profile server and press *Finish*. 
7. Go to: [http://localhost:9080/servlet](http://localhost:9080/servlet)

## Running with Maven

This project can be built with Apache Maven. The project uses Liberty Maven Plug-in to automatically download and install Liberty with Java EE7 Web Profile runtime from Maven Central. Liberty Maven Plug-in is also used to create, configure, and run the application on the Liberty server. 

Use the following steps to run the application with Maven:

1. Execute full Maven build. This will cause Liberty Maven Plug-in to download and install Liberty profile server.
    ```bash
    $ mvn clean install
    ```

2. To run the server with the Servlet sample execute:
    ```bash
    $ mvn liberty:run-server
    ```

Once the server is running, the application will be available under [http://localhost:9080/servlet](http://localhost:9080/servlet).

## Deploying to Bluemix

Click the button below to deploy your own copy of this application to [Bluemix](https://bluemix.net).

[![Deploy to Bluemix](https://bluemix.net/deploy/button.png)](https://bluemix.net/deploy?repository=https://github.com/WASdev/sample.servlet.git)

# Notice

© Copyright IBM Corporation 2017.

# License

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
````

[Liberty Maven Plug-in]: https://github.com/WASdev/ci.maven

