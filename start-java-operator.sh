export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.12.jdk/Contents/Home
minikube delete
minikube start
eval $(minikube docker-env)
cd dojo
mvn compile jib:dockerBuild
cd ..
cd python-local-service
docker build -t python-service .
cd ..
cd manifests
kubectl apply -f dojo-crd.yaml
kubectl apply -f operator.yaml
kubectl apply -f dojo.yaml
# kubectl exec -it $(kubectl get pods -n sample -o name | grep pod/operator-deployment-) -n sample /bin/bash
# apt update && apt install curl -y
# curl http://dojo-1.sample
