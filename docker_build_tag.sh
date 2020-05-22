aws ecr get-login-password --region af-south-1 | docker login --username AWS --password-stdin 316005840972.dkr.ecr.af-south-1.amazonaws.com
docker build -t ijudi-api .
docker tag ijudi-api:latest 316005840972.dkr.ecr.af-south-1.amazonaws.com/ijudi-api:latest
docker push 316005840972.dkr.ecr.af-south-1.amazonaws.com/ijudi-api:latest