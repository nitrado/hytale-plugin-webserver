# Service Accounts

Service Accounts are intended for processes that automatically interact with the server through HTTP APIs. For
security purposes, it is recommended to use service accounts that have the exact set of permissions to fulfill the
tasks they are intended for.

Service accounts will be automatically added to the `SERVICE_ACCOUNT` group to make them easier to identify in
permission management.

Service Accounts can be either created through the Web UI (not implemented yet) or provisioned automatically.

## Authenticating as a Service Account

You can use a Service Account password to authenticate against the web server using Basic Auth, such as with:

```bash
curl -u serviceaccount.MyServiceAccountName:MyPassword <url>
```

Note the `serviceaccount.` prefix when authenticating with a service account.

## Creation of Service Accounts through the Web UI

[[ TODO ]]

## Automatic Provisioning of Service Accounts

Create the folder `mods/Nitrado_WebServer/provisioning`. In it, you can place files that end in
`.serviceaccount.json`, such as `example.serviceaccount.json` with the following content structure:

```json
{
  "Enabled": true,
  "Name": "serviceaccount.example",
  "PasswordHash": "$2b$10$ME8G6/YZ3hXUOAhLs3mrh.a3cuZTvzE2zGjQIqxztgPXKtm7sFCde",
  "Groups": ["Creative"],
  "Permissions": ["nitrado.query.web.read.players"]
}
```

A service account with `Enabled` set to `true` will be automatically created or updated on server start. Setting
`Enabled` to `false` will lead to the service account to be removed, also removing it from any groups and permissions,
to not clutter your permission management.

### Configuration Fields

| Field         | Description                                                        |
|---------------|--------------------------------------------------------------------|
| `Enabled`     | Whether the service account should be active                       |
| `Name`        | The account name (must start with `serviceaccount.`)               |
| `PasswordHash`| A bcrypt hash of the password                                      |
| `Groups`      | List of permission groups the account belongs to                   |
| `Permissions` | List of individual permissions granted to the account              |

## Generating Password Hashes

Service account passwords must be stored as bcrypt hashes. You can generate these using common command-line tools.

### Using mkpasswd (recommended)

The `mkpasswd` utility is available on most Linux distributions (part of the `whois` package):

```bash
# Install on Debian/Ubuntu
sudo apt install whois

# Generate a bcrypt hash with cost factor 10
mkpasswd -m bcrypt -R 10 "YourSecurePassword"
```

Output example:
```
$2b$10$K8L9X7vZ3mN1pQ2wR4sT6uYhJkLmNoPqRsTuVwXyZaBcDeFgHiJkL
```

### Using htpasswd

The `htpasswd` utility from Apache can also generate bcrypt hashes:

```bash
# Install on Debian/Ubuntu
sudo apt install apache2-utils

# Generate a bcrypt hash (prints to stdout)
htpasswd -nbB -C 10 username "YourSecurePassword"
```

Output example:
```
username:$2y$10$K8L9X7vZ3mN1pQ2wR4sT6uYhJkLmNoPqRsTuVwXyZaBcDeFgHiJkL
```

Extract only the hash (everything after the first colon):

```bash
htpasswd -nbB -C 10 username "YourSecurePassword" | cut -d: -f2
```

### Using Python

If you have Python available:

```bash
python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourSecurePassword', bcrypt.gensalt(rounds=10)).decode())"
```

You may need to install the bcrypt package first:

```bash
pip install bcrypt
```

### Security Recommendations

- Use a cost factor of at least 10 for production environments
- Never commit plain-text passwords to version control
- Rotate service account passwords periodically
- Use unique passwords for each service account

