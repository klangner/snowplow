# Snowplow homework

## Run server

```sh
sbt run
```

Or run tests
```sh
sbt test
```

## Upload schema and verify document

```sh
curl -i http://localhost:8080/schema/config -X POST -d @data/config-schema.json
```

Get schema

```sh
curl http://localhost:8080/schema/config | python -m json.tool
```

Validate correct doc

```sh
curl -i http://localhost:8080/validate/config -X POST -d @data/config.json
```

Validate wrong doc

```sh
curl -i http://localhost:8080/validate/config -X POST -d @data/config-error.json
```
