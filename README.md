# quarkus-admin-sudothis

This is a free and open-source project to create a Django-style admin interface using Quarkus. It serves as a basic scaffold for secure user authentication, stateless session handling, and account administration.

## Overview

This project includes Terraform scripts to deploy the application into various environments using Docker containers.

Deployment scripts are symlinked in the project root:

* `local_deploy.sh`
* `dev_deploy.sh`
* `prod_deploy.sh` (requires additional configuration; see `terraform/environments/prod/`)

The production deployment expects a valid `main.tf` and populated variable definitions. Use `environment/local` as a reference and supply the necessary values.

## Technologies

* Quarkus (Java 17)
* Redis
* MySQL 8
* MailHog
* Docker
* Terraform

## Local Setup

To deploy locally:

```bash
cd quarkus-admin-sudothis/terraform/environments/local/
./terraform_deploy.sh
terraform apply "tf_out.txt"
```

## Development Mode

To run the application in development mode with live coding:

```bash
./mvnw quarkus:dev
```

The Quarkus Dev UI is available at:

```
http://localhost:8080/q/dev/
```

## Packaging the Application

To build the application:

```bash
./mvnw package
```

This will generate output in the `target/quarkus-app/` directory. To run the application:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

To build an uber-jar:

```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

## Native Executable

To build a native executable:

```bash
./mvnw package -Dnative
```

Or, using a container:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Run the executable with:

```bash
./target/quarkus-admin-sudothis-1.0.0-SNAPSHOT-runner
```

## Related Documentation

* [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
* [SmallRye Health Checks](https://quarkus.io/guides/smallrye-health)
* [MySQL JDBC Driver](https://quarkus.io/guides/datasource)
* [Quarkus REST](https://quarkus.io/guides/rest)
* [Redis Client](https://quarkus.io/guides/redis)
* [Jackson Serialization](https://quarkus.io/guides/rest#json-serialisation)

For general information, see the official Quarkus documentation:

[https://quarkus.io](https://quarkus.io)

## SSL and Certificate Management

Production deployments use a Terraform module to run Certbot in a container and sync certificates for automated SSL setup. See `terraform/modules/certbot-sync` and associated environment configurations.

Certbot documentation: [https://eff.org/certbot](https://eff.org/certbot)

Production deployments use a Terraform module to run Certbot in a container and sync certificates for automated SSL setup. See `terraform/modules/certbot-sync` and associated environment configurations.

## User Authentication and Authorization

This project uses a JPA-based Elytron security realm. Passwords are hashed with bcrypt. Sessions are managed through Redis.

## Admin Interface

The admin interface is rendered using Qute templates. It supports basic account management by authenticated users with administrative roles.

## Environment Configuration

Settings can be overridden per environment using the appropriate `application.properties` files. Sensitive values are injected via Terraform variables and secrets.

## Testing

Test coverage includes unit and integration tests for authentication.

Run tests with:

```bash
./mvnw test
```

## Known Limitations

* No frontend JavaScript enhancements
* Email sending is only available through MailHog in development
* Role-based access is minimal
* Certbot assumes Cloudflare DNS access and valid token
