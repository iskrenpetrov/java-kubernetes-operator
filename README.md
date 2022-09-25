## What does the operator do?
Kubernetes operator written in Java. The idea of the operator is when you apply crd and deployment, the operator create a deployment that includes python services with two rest apis(get request to receive current string and put request to edit the existing string), when you apply custom resources a new event is registered and for each minute, put request will be called to keep the string in the desired state from custom resources. Every time you apply new custom resources the operator will check if there is a existing python service and if there is no available deployment with the python application, it will create a new one.

## Prerequisites

* Python 3 (and pip)
* Minikube
* Java 11

## The steps until you reached "Testing the operator" section are automated in start-java-operator.sh
You can execute
 ```
 sh start-java-operator.sh
 ```

## Running the local python service

Navigate to the `python-local-service` folder and install the necessary dependencies (prefferably in a [virtual environemnt](https://towardsdatascience.com/managing-virtual-environment-with-pyenv-ae6f3fb835f8)) with

 ```
 pip install -r requirements.txt
 ```

You might have to use `pip3` instead, if you are not using a virtual environemnt.

After installing the dependencies run the application.

 ```
 python app.py
 ```

Again, you might have to use `python3` instead.

## Initiate a Kubernetes cluster with Minikube

To iniate a new cluster with Minikube it might be a good idea to first delete any traces of a previous playgrond. Use the `delete` command for that, forllowed by the `start` command

 ```
 minikube delete
 minikube start
 ```

Configure your local environment to reuse the Docker daemon inside the Minikube instance with the following shell command

 ```
 eval $(minikube docker-env)
 ```

## Deploy the Java Operator to the cluster

Navigate to the `dojo` folder.

Compile the Java application and build the Docker image containing it with the Maven plugin:

 ```
 mvn compile jib:dockerBuild
 ```

Apply the `dojo-crd` Custom Resource Definition to the cluster 

 ```
 kubectl apply -f manifests/dojo-crd.yaml
 ```

Deploy the operator to the cluster with the `operator.yaml` manifest

 ```
 kubectl apply -f manifests/operator.yaml 
 ```

You can verify the successful deployment by checking if the pod is running in kubernetes and inspecting the logs 

 ```
 kubectl get pods -n sample
 kubectl logs deployment.apps/operator-deployment -n sample
 ```

Apply the `Dojo` custom resource to the Kubernetes cluster

 ```
 kubectl apply -f manifests/dojo.yaml 
 ```

## Testing the operator
 Log into the operator deployment pod

 ```
 kubectl exec -it $(kubectl get pods -n sample -o name | grep pod/operator-deployment-) -n sample /bin/bash
 ```

 Check the initial status of the python service by opening a terminal and sending a `GET` request with `curl`
 

 ```
 apt update && apt install curl -y
 curl -X GET http://dojo-1.sample
 ```

You can change the string in the `content` field in the `dojo.yaml` manifest and every time it is applied it should be changed in the python service as well.
 
Deleting the resource should reset the String to an empty value

 ```
 kubectl delete -f manifest/dojo.yaml
 ```

### Testing the timed reconciliation

The operator will reconcile the value in the external service with the desired value stated in the `dojo` custom resource regularly. Even if the value is changed not through the CR desired state

After deploying the operator and the custom resource, change the value directly in the external service with `curl`

 ```
 curl -X PUT "http://dojo-1.sample?content=some-new-value"
 ```

Observe the logs of the operator and make sure that after the reconciliation is triggered value has returned to the desired state

 ```
 kubectl logs deployment.apps/operator-deployment -n sample
 ```