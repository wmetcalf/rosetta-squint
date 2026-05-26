# Releasing rosetta-squint

This document covers how to cut a new release across all 5 registries. Run the steps in order — most registries have inter-crate ordering dependencies (the umbrella package needs the libs published first).

Currently published versions: **1.0.0** (see [CHANGELOG.md](./CHANGELOG.md)).

## Quick map

| Registry | Packages | Auth | One-shot publish? |
|---|---|---|---|
| PyPI | `rosetta-squint-hash`, `rosetta-squint` | API token (`~/.pypirc`) | Yes |
| crates.io | `rosetta-squint-hash`, `rosetta-squint-decode`, `rosetta-squint` | `cargo login <token>` | No — must publish in order, index propagation delay |
| npm | `rosetta-squint-hash`, `rosetta-squint-decode`, `rosetta-squint` | `npm login` | Yes |
| Maven Central | `io.github.wmetcalf:rosetta-squint-hash`, `rosetta-squint-decode`, `rosetta-squint` | OSSRH/Central user token + GPG key | No — staging then release |
| SwiftPM | git tag only — no central registry | git push | Implicit |
| Go modules | git tag only — `pkg.go.dev` discovers via tag | git push | Implicit |

## Pre-release checklist

1. All CI workflows green (`gh run list --branch main --limit 4`).
2. Cross-port-diff sanity passes: `make cross-squint-diff` (35 grid × 2 fixtures = 70 byte-exact verifications).
3. `CHANGELOG.md` updated with this version's entry.
4. Manifest versions all match: grep for the OLD version string across `**/pyproject.toml`, `**/Cargo.toml`, `**/package.json`, `**/pom.xml` — none should remain. Bump them via `bumpversion` or by hand if not configured.
5. Working tree clean: `git status -s` empty.

## PyPI

```bash
# One-time: create API token at https://pypi.org/manage/account/token/
# Save to ~/.pypirc:
#   [pypi]
#   username = __token__
#   password = pypi-...

# Per-package:
cd hash/python                                     # then `cd squint/python`
rm -rf dist/ build/ *.egg-info
python3 -m build                                   # → dist/*.tar.gz + dist/*.whl
python3 -m twine check dist/*                      # must say PASSED
python3 -m twine upload dist/*                     # publishes to PyPI

# Verify:
pip install --upgrade rosetta-squint-hash          # should pull 1.0.0
```

## crates.io

```bash
# One-time: get token from https://crates.io/me, then:
cargo login

# Publish order matters — umbrella crate has path+version deps on the libs,
# so libs publish first and the index needs ~30-60 seconds to propagate
# before the umbrella can find them.

cd hash/rust/rosetta-squint-hash
cargo publish --dry-run                            # verify
cargo publish

# Wait ~30-60 seconds for index propagation
sleep 60

cd ../../../decode/rust/rosetta-squint-decode
cargo publish --dry-run
cargo publish

sleep 60

cd ../../../squint/rust/rosetta-squint
cargo publish --dry-run
cargo publish
```

If `--dry-run` shows the path-dep / version-dep mismatch error for the umbrella, that means the libs haven't propagated yet — wait longer.

## npm

```bash
# One-time:
npm login                                          # prompts for username/password/email

# Publish (any order — npm packages are independent):
for d in hash/js/rosetta-squint-hash decode/js/rosetta-squint-decode squint/js/rosetta-squint; do
  (cd "$d" && npm run build && npm publish --access public)
done
```

The `--access public` is required because the package name has no scope prefix; npm defaults to "private" for paid accounts without it.

## Maven Central

Maven Central is the hardest registry to set up because of namespace verification + GPG signing requirements.

### One-time setup (user account)

1. **Register Sonatype Central Portal** account at https://central.sonatype.com (sign in via GitHub for auto-verification).

2. **Verify `io.github.wmetcalf` namespace** via the Central Portal UI — for a `io.github.<username>` groupId, verification is automatic against the GitHub account. (For a custom domain like `io.rosetta`, you'd need a DNS TXT record on `rosetta.io` — we use the GitHub path instead.)

3. **Generate a user token** at https://central.sonatype.com/account → "User Token". This gives you a `username` + `password` pair (NOT your account password).

### One-time setup (workstation)

4. **Add the user token to `~/.m2/settings.xml`** (create if absent):

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>USER-TOKEN-USERNAME-HERE</username>
         <password>USER-TOKEN-PASSWORD-HERE</password>
       </server>
     </servers>
   </settings>
   ```

   `chmod 600 ~/.m2/settings.xml`.

5. **Generate a GPG key** (if you don't already have one):

   ```bash
   gpg --full-generate-key                         # → choose RSA 4096, no expiry
   gpg --list-secret-keys --keyid-format=long      # note the keyid (16 hex chars)
   ```

6. **Upload your public key** to keyservers so Maven Central can verify signatures:

   ```bash
   gpg --keyserver keys.openpgp.org --send-keys YOUR_KEYID_HERE
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEYID_HERE
   ```

7. **(Optional, but recommended) Configure gpg-agent for passphrase caching** so `mvn deploy` doesn't prompt for each artifact:

   ```bash
   echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
   gpg-connect-agent reloadagent /bye
   ```

### Publish

```bash
# Publish in order: libs first, then umbrella. Maven resolves transitively
# from staging, so each must finish staging+closing before the next.

# 1) Install all three locally first (so the umbrella can build against them)
(cd hash/java                              && mvn -B -ntp install -DskipTests)
(cd decode/java/rosetta-squint-decode      && mvn -B -ntp install -DskipTests)
(cd squint/java/rosetta-squint             && mvn -B -ntp install -DskipTests)

# 2) Publish hash
cd hash/java
mvn -Prelease deploy                       # builds source+javadoc jars, signs, uploads to staging
# Verify staging at https://central.sonatype.com/publishing/deployments
# Click "Publish" in the UI (or set autoPublish=true in the pom plugin config)

# 3) Wait for hash to appear at search.maven.org (~10-30 min after release)
# Verify: curl -sf https://repo1.maven.org/maven2/io/github/wmetcalf/rosetta-squint-hash/1.0.0/

# 4) Publish decode (same flow)
cd ../../decode/java/rosetta-squint-decode
mvn -Prelease deploy

# 5) Publish squint umbrella (last — depends on the libs)
cd ../../../squint/java/rosetta-squint
mvn -Prelease deploy
```

### Troubleshooting

- **"401 Unauthorized"** during deploy: token in `~/.m2/settings.xml` is wrong or not for `id=central`.
- **"403 Forbidden"** during deploy: namespace `io.github.wmetcalf` not yet verified in Central Portal.
- **"GPG signing failed"**: `maven-gpg-plugin` can't find the key. Try `gpg --list-secret-keys`; if empty, regenerate. If the key exists but mvn can't sign, your gpg-agent may not be configured for loopback (see step 7).
- **"can't find dependency rosetta-squint-hash:1.0.0"** when deploying squint: the hash artifact isn't in the staging repo yet, or hasn't been "Published" via the UI. The umbrella deploys last.

## Tagging

After every successful release:

```bash
git tag -a v1.0.0 -m "rosetta-squint v1.0.0 — initial publish to PyPI/crates.io/npm/Maven Central"
git push origin v1.0.0
```

The Go modules (`hash/go/imagehash`, `decode/go/imagedecode`, `squint/go/squint`) are discoverable via `pkg.go.dev` once tagged — no separate publish step.

The Swift packages (`hash/swift/RosettaSquintHash`, `decode/swift/RosettaSquintDecode`, `squint/swift/RosettaSquint`) are likewise consumed by SwiftPM directly from the git URL — the tag is what consumers pin to.

## Yanking / un-publishing

Each registry has different policies. None lets you actually delete a version, only mark it as deprecated/yanked.

| Registry | How | When |
|---|---|---|
| PyPI | `pip` page → "Yank release" | Anytime; existing pinned installs still work |
| crates.io | `cargo yank --version 1.0.0 rosetta-squint-hash` | Anytime; existing Cargo.lock entries still work |
| npm | `npm unpublish rosetta-squint-hash@1.0.0` | **Only within 72 hours of publish**; after that, must contact npm support |
| Maven Central | **Cannot be deleted.** Cut a new patch version with the fix. | Never |

Plan accordingly: get the version + naming right BEFORE publishing, especially for Maven Central.
