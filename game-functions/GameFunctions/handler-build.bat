@echo off
echo Building JAR...
call mvn clean package -DskipTests

echo Building native image...
docker build -t lambda-native-builder .

echo Extracting bootstrap binary...
docker create --name extract lambda-native-builder
docker cp extract:/var/runtime/bootstrap ./bootstrap
docker rm extract

echo Creating ZIP package...
powershell Compress-Archive -Path bootstrap -DestinationPath ActionHandler.zip -Force

del bootstrap

echo Build complete! ActionHandler.zip is ready for deployment.