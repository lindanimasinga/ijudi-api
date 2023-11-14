mvn package -DincludeScope=runtime
aws ecr get-login-password --region af-south-1 | docker login --username AWS --password-stdin 316005840972.dkr.ecr.af-south-1.amazonaws.com
docker build --platform linux/amd64 -t izinga-api-lambda .
docker tag izinga-api-lambda:latest 316005840972.dkr.ecr.af-south-1.amazonaws.com/izinga-api-lambda:latest
docker push 316005840972.dkr.ecr.af-south-1.amazonaws.com/izinga-api-lambda:latest