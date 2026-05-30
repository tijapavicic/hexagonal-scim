# Keytool Cheatsheet

Quick reference for `keytool` with copy-paste commands for local development and ops tasks.

## Notes

- Default Java keystore type is `PKCS12` on modern JDKs.
- Use strong passwords and avoid committing certs/keystores to git.
- Replace placeholders in angle brackets, for example `<alias>`.

## Check Keytool and Java

```bash
keytool -help | head -n 20
java -version
```

## Generate a New Keystore and Self-Signed Cert

```bash
keytool -genkeypair \
  -alias app-local \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -validity 825 \
  -storetype PKCS12 \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>' \
  -dname 'CN=localhost, OU=Dev, O=Example, L=City, ST=State, C=US' \
  -ext 'SAN=dns:localhost,ip:127.0.0.1'
```

## List Keystore Entries

```bash
keytool -list -v \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

List a specific alias:

```bash
keytool -list -v \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

## Export a Certificate (Public Cert)

PEM format:

```bash
keytool -exportcert \
  -rfc \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -file app-local.crt
```

DER format:

```bash
keytool -exportcert \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -file app-local.der
```

## Create a CSR (Certificate Signing Request)

```bash
keytool -certreq \
  -alias app-local \
  -file app-local.csr \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>'
```

With SAN extension:

```bash
keytool -certreq \
  -alias app-local \
  -file app-local.csr \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -ext 'SAN=dns:localhost,dns:app.local,ip:127.0.0.1'
```

## Import Certificates

Import CA/root/intermediate into keystore or truststore:

```bash
keytool -importcert \
  -alias my-ca \
  -file ca.crt \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass '<storepass>' \
  -noprompt
```

Import signed certificate reply for existing keypair alias:

```bash
keytool -importcert \
  -alias app-local \
  -file signed-cert.crt \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>'
```

If your CA provides a chain, import root/intermediate first, then import signed cert reply to key alias.

## Verify Certificate File

```bash
keytool -printcert -v -file app-local.crt
```

Verify CSR details:

```bash
keytool -printcertreq -v -file app-local.csr
```

## Change Alias and Passwords

Change alias:

```bash
keytool -changealias \
  -alias old-alias \
  -destalias new-alias \
  -keystore app-keystore.p12 \
  -storepass '<storepass>' \
  -keypass '<keypass>'
```

Change keystore password:

```bash
keytool -storepasswd \
  -new '<new-storepass>' \
  -keystore app-keystore.p12 \
  -storepass '<old-storepass>'
```

Change key password:

```bash
keytool -keypasswd \
  -alias app-local \
  -keypass '<old-keypass>' \
  -new '<new-keypass>' \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

Delete alias:

```bash
keytool -delete \
  -alias app-local \
  -keystore app-keystore.p12 \
  -storepass '<storepass>'
```

## Convert Between JKS and PKCS12

JKS to PKCS12:

```bash
keytool -importkeystore \
  -srckeystore app.jks \
  -srcstoretype JKS \
  -srcstorepass '<srcpass>' \
  -destkeystore app.p12 \
  -deststoretype PKCS12 \
  -deststorepass '<destpass>'
```

PKCS12 to JKS:

```bash
keytool -importkeystore \
  -srckeystore app.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass '<srcpass>' \
  -destkeystore app.jks \
  -deststoretype JKS \
  -deststorepass '<destpass>'
```

## Truststore Operations (JVM and App)

Create app truststore and import certs:

```bash
keytool -importcert \
  -alias internal-api-ca \
  -file internal-api-ca.crt \
  -keystore app-truststore.p12 \
  -storetype PKCS12 \
  -storepass '<storepass>' \
  -noprompt
```

Use truststore at runtime:

```bash
java \
  -Djavax.net.ssl.trustStore=app-truststore.p12 \
  -Djavax.net.ssl.trustStoreType=PKCS12 \
  -Djavax.net.ssl.trustStorePassword='<storepass>' \
  -jar app.jar
```

Inspect default JVM truststore (`cacerts`) path example:

```bash
"$JAVA_HOME"/bin/keytool -list -cacerts -storepass changeit | head -n 40
```

## Spring Boot TLS Example

`application.yml` example values:

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:tls/app-keystore.p12
    key-store-type: PKCS12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-alias: app-local
```

## SAN Quick Examples

- Localhost only: `-ext 'SAN=dns:localhost,ip:127.0.0.1'`
- Local network: `-ext 'SAN=dns:myhost.local,ip:192.168.1.10'`
- Multiple DNS names: `-ext 'SAN=dns:api.local,dns:app.local,dns:localhost'`

## Troubleshooting

- `Alias <x> does not exist`: check with `keytool -list -keystore <file>`.
- `Keystore was tampered with, or password incorrect`: verify store type and password.
- `Failed to establish chain from reply`: import CA chain first, then cert reply.
- TLS hostname mismatch: regenerate cert with correct SAN entries.
- Wrong file type: confirm with `file <keystore>` and explicit `-storetype`.

## Secure Practices

- Keep keystore/truststore passwords in environment variables or secret managers.
- Use separate certs for dev/test/prod.
- Rotate certificates before expiration and track renewal dates.
- Restrict file permissions:

```bash
chmod 600 app-keystore.p12
chmod 600 app-truststore.p12
```

- Avoid sharing private key material (`.p12`, `.jks`) in chat, tickets, or commits.

