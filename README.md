# embulk-awslambda-packager

embulk-awslambda-packager is embulk packaging tool for embulk on aws lambda.

## getting started

### checkout
```bash
git clone https://github.com/mitoma/embulk-awslambda-packager.git embulk-awslambda-packager
cd embulk-awslambda-packager
```
### build

```bash
./gradlew clean build \
 -PembulkPlugins="[plugin names (delimiter = space)]" \
 -PgemsS3BucketName="[bucket name of gems.zip]"
 -PgemsS3Key="[path of gems.zip]" \
 -PembulkConfig="[your config path on local machine]" \
 -PuseExternalConfig="[if you want to use config on s3, set true]" \
 -PexternalConfigS3BucketName="[bucket name of config file]" \
 -PexternalConfigS3Key="[path of config file]" \
 -PoverWriteExternalConfig="[if you want to overwrite config file, set true. (like -o option)]"
```
 
### upload

if build succeeded, script generate two zip files.
you need to upload to s3.

- build/distributions/awslambda.zip -> any place.
- build/distributions/gems.zip -> s3://[gemsS3BucketName]/[gemsS3Key]

### setup lambda function.

create lambda funciton.

- Runtime -> Java8
- Upload a .ZIP from Amazon S3 -> awslambda.zip's path
- handler -> in.tombo.embulk.AwsLambdaExecutor::executeByScheduledEvent or in.tombo.embulk.AwsLambdaExecutor::executeByS3Event

## todo

- [ ] document
- [ ] support some event trigger
