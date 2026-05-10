# CI/CD Explained — What We Did, Why, and How to Use It

> **Audience**: new team members  
> **Goal**: after reading this you will understand what CI/CD is, what was built for this project, how every line in the workflow files works, and what you need to do to use it.

---

## Table of Contents

1. [What is CI/CD? (The Big Picture)](#1-what-is-cicd-the-big-picture)
2. [What Technology Are We Using?](#2-what-technology-are-we-using)
3. [What Was Built for This Project?](#3-what-was-built-for-this-project)
4. [File Map — Where Everything Lives](#4-file-map--where-everything-lives)
5. [Deep Dive — CI Workflow (`ci.yml`)](#5-deep-dive--ci-workflow-ciyml)
6. [Deep Dive — Release Workflow (`release.yml`)](#6-deep-dive--release-workflow-releaseyml)
7. [The OWASP Security Gate — What It Is and Why](#7-the-owasp-security-gate--what-it-is-and-why)
8. [Docker Images — What Gets Built and Where They Go](#8-docker-images--what-gets-built-and-where-they-go)
9. [Automatic Versioning — How Version Numbers Work](#9-automatic-versioning--how-version-numbers-work)
10. [What You Need to Do Before First Use (One-Time Setup)](#10-what-you-need-to-do-before-first-use-one-time-setup)
11. [Day-to-Day Developer Workflow](#11-day-to-day-developer-workflow)
12. [Reading the GitHub Actions UI](#12-reading-the-github-actions-ui)
13. [What Happens When a Build Fails](#13-what-happens-when-a-build-fails)
14. [Secrets — How Credentials Are Stored Safely](#14-secrets--how-credentials-are-stored-safely)
15. [Glossary](#15-glossary)

---

## 1. What is CI/CD? (The Big Picture)

**CI = Continuous Integration**  
Automatically build and test your code every time someone pushes a commit or opens a pull request.  
"Continuous" means it happens on every single change — you don't wait for a weekly build meeting.

**CD = Continuous Delivery (or Deployment)**  
Automatically package, version, and publish your software every time the CI gate passes.  
In this project it means: build Docker images, push them to a registry, create a Git tag, and create a GitHub Release.

### Why bother?

| Without CI/CD | With CI/CD |
|---------------|------------|
| "It works on my machine" bugs ship to production | Tests run on a clean machine every time |
| Someone forgets to run tests before merging | Impossible to merge if tests fail |
| You find security vulnerabilities after releasing | CVE scan blocks the build before shipping |
| Releases are manual, error-prone, stressful | Releases are a button click (or automatic) |
| Nobody knows what is in production | Every image is tagged with the exact Git commit |

---

## 2. What Technology Are We Using?

### GitHub Actions

GitHub Actions is GitHub's built-in automation system.  
It is **free for public repositories** and has a monthly free quota for private ones.

You write **workflow files** in YAML (a simple text format) inside `.github/workflows/`.  
GitHub reads these files and runs them on virtual machines (called **runners**) in the cloud.

No servers to maintain. No Jenkins to configure. Everything lives in the same repository as your code.

### YAML files

YAML is a human-readable configuration format. Think of it like JSON but nicer to read.

```yaml
name: My First Workflow        # the display name in GitHub UI
on:                            # when to run
  push:
    branches: [ "main" ]       # run when someone pushes to main
jobs:
  say-hello:                   # job name
    runs-on: ubuntu-latest     # run on a Linux virtual machine
    steps:
      - name: Print a message  # step name
        run: echo "Hello CI!"  # the actual shell command
```

### Docker

Docker packages your application into a **container** — a self-contained box that includes the application, JRE, and all dependencies.  
The result is a Docker **image** that runs identically on any machine (your laptop, a server in AWS, on ARM or Intel).

### GitHub Container Registry (ghcr.io)

Where Docker images are stored after they are built.  
Think of it like npm for JavaScript packages, but for Docker images.  
Each image is tagged with a version number so you can always pull exactly the version you deployed.

---

## 3. What Was Built for This Project?

Two workflow files live in `.github/workflows/`:

### `ci.yml` — runs on every PR and every push to main

```
PR opened / commit pushed
        │
        ▼
┌───────────────────┐
│  1. Build & Test  │  mvn clean verify (all 49 tests)
└─────────┬─────────┘
          │ passes
          ├──────────────────────────────────┐
          ▼                                  ▼
┌──────────────────────┐          ┌──────────────────────┐
│  2. OWASP CVE Scan   │          │  3. Docker Build      │
│  (security gate)     │          │  (verify it compiles) │
└──────────────────────┘          └──────────────────────┘
```

### `release.yml` — runs on every merge to main

```
Merged to main
        │
        ▼
┌──────────────────────┐
│  1. Calculate next   │  v0.0.2 → v0.0.3 automatically
│     version number   │
└─────────┬────────────┘
          │
          ▼
┌──────────────────────┐
│  2. Build + Test +   │  same quality gate, again
│     OWASP gate       │
└─────────┬────────────┘
          │ passes
          ├──────────────────────────────────┐
          ▼                                  ▼
┌──────────────────────┐          ┌──────────────────────┐
│  3. Push Docker      │          │  4. Git tag +        │
│     images to        │          │     GitHub Release   │
│     ghcr.io          │          │                      │
└──────────────────────┘          └──────────────────────┘
```

---

## 4. File Map — Where Everything Lives

```
hexagonal-scim/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                  ← CI pipeline (PR + push to main)
│   │   └── release.yml             ← Release pipeline (merge to main)
│   └── owasp-suppressions.xml      ← Ignore false-positive CVEs here
│
├── Dockerfile                      ← Backend image recipe
└── frontend/
    └── Dockerfile                  ← Frontend Nginx image recipe
```

---

## 5. Deep Dive — CI Workflow (`ci.yml`)

```yaml
name: CI
```
This is the name shown in the GitHub Actions tab.

---

### When does it run?

```yaml
on:
  push:
    branches: [ "main" ]     # someone pushes directly to main
  pull_request:
    branches: [ "main" ]     # someone opens a PR targeting main
  workflow_dispatch:          # you can click "Run workflow" manually in the UI
```

**`workflow_dispatch`** is useful for debugging — go to Actions tab → select CI → click "Run workflow".

---

### Concurrency — preventing wasted work

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

If you push 5 commits quickly, there is no point running CI 5 times.  
This setting **cancels the previous run** when a newer commit arrives.  
`github.ref` is the branch name — so cancellation only happens for the same branch.

---

### Job 1 — Build & Test

```yaml
build-and-test:
  runs-on: ubuntu-latest
```

GitHub spins up a fresh Ubuntu Linux virtual machine. Your laptop is not involved.

```yaml
  steps:
    - name: Checkout
      uses: actions/checkout@v4
```

`actions/checkout` is a pre-built action (think: a reusable script) made by GitHub.  
`@v4` means version 4. It downloads your repository code onto the runner.

```yaml
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: 'maven'
```

Installs Java 17 (Eclipse Temurin — same JDK used in the Docker image).  
`cache: 'maven'` — caches your `~/.m2` Maven dependency download folder between runs.  
First run: downloads all jars (~2 minutes). Every run after: uses the cache (~20 seconds).

```yaml
    - name: Build and run all tests
      run: mvn -B --no-transfer-progress clean verify
```

`-B` = batch mode (no interactive prompts)  
`--no-transfer-progress` = less noisy output  
`clean verify` = from scratch: compile, test, check

If any test fails → the job fails → the PR cannot be merged (GitHub blocks it).

```yaml
    - name: Upload test reports
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-reports
        path: |
          **/target/surefire-reports/
          **/target/failsafe-reports/
        retention-days: 7
```

`if: always()` — upload even if tests FAILED (so you can read the failure report).  
`upload-artifact` stores the test XML reports in GitHub for 7 days.  
You can download them from the Actions run page (bottom of the page → Artifacts section).

---

### Job 2 — OWASP Dependency Check

```yaml
owasp-check:
  needs: build-and-test
```

`needs: build-and-test` — this job only starts if Job 1 passed.  
No point checking CVEs if the code doesn't even compile.

```yaml
    - name: Cache OWASP NVD data
      uses: actions/cache@v4
      with:
        path: ~/.m2/repository/org/owasp/dependency-check-data
        key: owasp-nvd-${{ runner.os }}-${{ hashFiles('**/pom.xml') }}
```

The National Vulnerability Database (NVD) is ~200 MB. Without caching, it re-downloads every run.  
`hashFiles('**/pom.xml')` — the cache key changes when any `pom.xml` changes.  
When pom changes → fresh NVD download. Otherwise → use cache.

```yaml
    - name: Run OWASP Dependency Check (CVSS fail threshold = 7)
      run: |
        mvn -B org.owasp:dependency-check-maven:check \
          -DfailBuildOnCVSS=7 \
          -DsuppressionFile=.github/owasp-suppressions.xml \
          -Dformats=HTML,JSON
```

`-DfailBuildOnCVSS=7` — CVSS score goes from 0 to 10. Score ≥ 7 = HIGH/CRITICAL → build fails.  
`-DsuppressionFile` — some CVEs are false positives (the library is present but the vulnerable code path is not used). You whitelist them in `owasp-suppressions.xml`.  
`-Dformats=HTML,JSON` — generate a human-readable HTML report and a machine-readable JSON report.

> **CVSS Score Reference**
> | Score | Severity |
> |-------|----------|
> | 9.0–10.0 | 🔴 CRITICAL |
> | 7.0–8.9 | 🟠 HIGH |
> | 4.0–6.9 | 🟡 MEDIUM |
> | 0.1–3.9 | 🟢 LOW |

---

### Job 3 — Docker Build Verification

```yaml
docker-build:
  needs: build-and-test
```

Runs in parallel with OWASP check (both need Job 1, neither needs each other).

```yaml
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v3
```

Buildx is Docker's modern multi-architecture build tool. Required to build for both `amd64` (Intel/AMD) and `arm64` (Apple Silicon, AWS Graviton).

```yaml
    - name: Build backend image (no push)
      uses: docker/build-push-action@v6
      with:
        push: false                              # do NOT push — only verify it builds
        platforms: linux/amd64,linux/arm64       # build for both chip architectures
        cache-from: type=gha                     # use GitHub Actions layer cache
        cache-to: type=gha,mode=max              # save layers to cache after build
```

`push: false` — this job only VERIFIES the Dockerfile is correct. Actual push happens in `release.yml`.  
`type=gha` — "gha" = GitHub Actions cache. Docker layer caching stored in GitHub Actions cache storage.

---

## 6. Deep Dive — Release Workflow (`release.yml`)

### When does it run?

```yaml
on:
  push:
    branches: [ "main" ]    # every merge to main
  workflow_dispatch:
    inputs:
      version:
        description: 'Override version tag (e.g. v0.0.5). Leave blank to auto-increment patch.'
```

The `workflow_dispatch` input lets you manually trigger with a specific version like `v1.0.0`.  
Leave it blank → auto-increment: `v0.0.2` → `v0.0.3`.

---

### Job 1 — Determine Next Version

```yaml
version:
  outputs:
    tag: ${{ steps.calc.outputs.tag }}
```

This job **outputs** the version tag so other jobs can use it.

```bash
# Find the latest vX.Y.Z tag; default to v0.0.0 if none exists
LATEST=$(git tag --list 'v*.*.*' --sort=-version:refname | head -n1)
if [ -z "$LATEST" ]; then
  LATEST="v0.0.0"
fi
# Strip leading 'v', split, increment patch
IFS='.' read -r MAJOR MINOR PATCH <<< "${LATEST#v}"
PATCH=$((PATCH + 1))
TAG="v${MAJOR}.${MINOR}.${PATCH}"
echo "tag=${TAG}" >> "$GITHUB_OUTPUT"
```

Step by step:
1. `git tag --list 'v*.*.*' --sort=-version:refname` — list all tags matching `vX.Y.Z` in descending order
2. `head -n1` — take the top one (latest)
3. If no tags exist yet → start at `v0.0.0`
4. Split `v0.0.2` into `MAJOR=0 MINOR=0 PATCH=2`
5. Increment: `PATCH=3`
6. Output: `v0.0.3`
7. `echo "tag=..." >> "$GITHUB_OUTPUT"` — this is how jobs share data in GitHub Actions

---

### Job 2 — Quality Gate

Same as CI: build, test, OWASP scan. Must all pass before any Docker push or Git tag.

```yaml
gate:
  needs: version
```

Runs after the version number is known.

---

### Job 3 — Docker Push

```yaml
docker-push:
  needs: [ version, gate ]
  permissions:
    packages: write    # required to push to ghcr.io
```

```yaml
    - name: Log in to GitHub Container Registry
      uses: docker/login-action@v3
      with:
        registry: ghcr.io
        username: ${{ github.actor }}          # your GitHub username
        password: ${{ secrets.GITHUB_TOKEN }}  # auto-provided by GitHub, no setup needed
```

`GITHUB_TOKEN` is automatically available in every GitHub Actions job — you don't create it.  
GitHub generates it fresh for each run and it expires after the run finishes.

```yaml
    - name: Push backend image
      uses: docker/build-push-action@v6
      with:
        push: true          # now we actually push
        tags: |
          ghcr.io/${{ github.repository }}/app:${{ needs.version.outputs.tag }}
          ghcr.io/${{ github.repository }}/app:latest
```

After merging `v0.0.3`, two tags are pushed:
- `ghcr.io/your-org/hexagonal-scim/app:v0.0.3` — pinned to this exact version
- `ghcr.io/your-org/hexagonal-scim/app:latest` — always points to the newest

```yaml
        labels: |
          org.opencontainers.image.source=...     # links image to GitHub repo
          org.opencontainers.image.revision=...   # exact Git commit SHA
          org.opencontainers.image.version=...    # vX.Y.Z
```

OCI labels are metadata burned into the image. When someone pulls the image they can see exactly which Git commit it was built from.

---

### Job 4 — Git Tag & GitHub Release

```yaml
tag-and-release:
  needs: [ version, docker-push ]
  permissions:
    contents: write    # required to create tags and releases
```

```yaml
    - name: Create annotated Git tag
      run: |
        git config user.name  "github-actions[bot]"
        git config user.email "github-actions[bot]@users.noreply.github.com"
        git tag -a "${{ needs.version.outputs.tag }}" \
          -m "Release ${{ needs.version.outputs.tag }} — auto-tagged by release workflow"
        git push origin "${{ needs.version.outputs.tag }}"
```

The bot creates a real Git tag on the repository. You will see it on the GitHub Tags page.  
`-a` = annotated tag (has a message, shows in `git log --tags`).

```yaml
    - name: Create GitHub Release
      uses: softprops/action-gh-release@v2
      with:
        tag_name: ${{ needs.version.outputs.tag }}
        generate_release_notes: true    # auto-generates notes from merged PRs
        body: |
          ## Docker images
          ghcr.io/your-repo/app:v0.0.3
          ghcr.io/your-repo/frontend:v0.0.3
```

`generate_release_notes: true` — GitHub reads all merged PR titles since the last tag and generates a changelog automatically. You don't have to write release notes manually.

---

## 7. The OWASP Security Gate — What It Is and Why

**OWASP** = Open Web Application Security Project — a non-profit that maintains security standards and tools.

**dependency-check** is their tool that:
1. Reads your `pom.xml` to list all dependencies
2. Downloads the **National Vulnerability Database (NVD)** — a public list of all known CVEs
3. Matches your dependency versions against the NVD
4. Fails the build if any match has a CVSS score ≥ 7 (HIGH or CRITICAL)

### Example CVE report

```
[ERROR] CVE-2023-12345 (CVSS 9.1 CRITICAL)
  Dependency: com.example:some-library:1.0.0
  Description: Remote code execution via crafted HTTP request
  Fix: Upgrade to 1.0.1
```

### What to do when a CVE is found

**Option 1 — Upgrade the dependency** (preferred)

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>some-library</artifactId>
  <version>1.0.1</version>   <!-- upgraded from 1.0.0 -->
</dependency>
```

**Option 2 — Suppress it** (only if it is a confirmed false positive)

In `.github/owasp-suppressions.xml`:
```xml
<suppress until="2026-12-31Z">
  <notes>False positive — we do not use the affected parse() method.
         The vulnerable code path is dead code in our usage. Tracked: JIRA-1234.</notes>
  <packageUrl regex="true">^pkg:maven/com\.example/some\-library@.*$</packageUrl>
  <cve>CVE-2023-12345</cve>
</suppress>
```

`until` = expiry date. Suppression auto-expires — forces you to re-evaluate.

---

## 8. Docker Images — What Gets Built and Where They Go

### Two images are built

| Image | Dockerfile | What it contains |
|-------|-----------|-----------------|
| Backend (`app`) | `Dockerfile` | Spring Boot fat JAR + JRE 17 (Ubuntu Jammy) |
| Frontend | `frontend/Dockerfile` | React build artifacts + Nginx TLS proxy |

### CI vs Release

| Workflow | Builds? | Pushes? |
|----------|---------|---------|
| `ci.yml` | ✅ Yes | ❌ No — verify only |
| `release.yml` | ✅ Yes | ✅ Yes → ghcr.io |

### How to pull a released image

```bash
# Pull the latest
docker pull ghcr.io/your-org/hexagonal-scim/app:latest

# Pull an exact version
docker pull ghcr.io/your-org/hexagonal-scim/app:v0.0.3

# See what commit it was built from
docker inspect ghcr.io/your-org/hexagonal-scim/app:v0.0.3 \
  --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'
```

### Multi-arch — why two platforms?

`platforms: linux/amd64,linux/arm64`

- `amd64` = Intel / AMD chips (most servers, older MacBooks)
- `arm64` = Apple Silicon (M1/M2/M3 Macs), AWS Graviton (cheaper cloud servers)

When you `docker pull`, Docker automatically picks the right variant for your machine.  
Without multi-arch you would get errors like the one we fixed earlier (`no match for platform in manifest`).

---

## 9. Automatic Versioning — How Version Numbers Work

We use **Semantic Versioning (SemVer)**:

```
v  MAJOR  .  MINOR  .  PATCH
v    0    .    0    .    3
```

| Segment | Increment when... | Example |
|---------|------------------|---------|
| PATCH | Bug fixes, small improvements | `v0.0.2` → `v0.0.3` |
| MINOR | New features, backwards compatible | `v0.0.3` → `v0.1.0` |
| MAJOR | Breaking changes | `v0.1.0` → `v1.0.0` |

**The CI/CD pipeline always auto-increments PATCH.**  
For MINOR or MAJOR bumps, trigger the release workflow manually and type the version:

```
Actions → Release → Run workflow → version: v1.0.0
```

---

## 10. What You Need to Do Before First Use (One-Time Setup)

### Step 1 — Enable GitHub Actions

Go to your repository → Settings → Actions → General → Allow all actions.  
(Usually already enabled for new repositories.)

### Step 2 — Enable write permissions for packages

Go to: Settings → Actions → General → Workflow permissions  
Select: **Read and write permissions**  
This allows the release workflow to push images to `ghcr.io`.

### Step 3 — Add NVD API Key (optional but recommended)

The OWASP scan downloads the NVD database. Without an API key you are rate-limited (slow downloads).  
Get a free key from: `https://nvd.nist.gov/developers/request-an-api-key`

Go to: Settings → Secrets and variables → Actions → New repository secret  
- Name: `NVD_API_KEY`  
- Value: `your-key-here`

That's it. The workflow reads `${{ secrets.NVD_API_KEY }}` automatically.

### Step 4 — Protect the main branch (recommended)

Go to: Settings → Branches → Add rule → Branch name: `main`  
Enable:
- ✅ Require a pull request before merging
- ✅ Require status checks to pass before merging
  - Add: `Build & Test (Java 17)`
  - Add: `OWASP Dependency Check`
  - Add: `Docker Build Verification`

Now nobody (not even you) can merge a PR that fails CI.

---

## 11. Day-to-Day Developer Workflow

```
                         ┌──────────────────────────────────┐
                         │           YOUR MACHINE            │
                         │                                   │
                         │  git checkout -b feat/my-feature  │
                         │  ... write code ...               │
                         │  mvn clean verify  ← run locally  │
                         │  git push origin feat/my-feature  │
                         └──────────────┬───────────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────────┐
                         │           GITHUB                  │
                         │  Open Pull Request                │
                         │  ↓                                │
                         │  ci.yml runs automatically:       │
                         │    ✅ Build & Test                │
                         │    ✅ OWASP Scan                  │
                         │    ✅ Docker Build                │
                         │  ↓                                │
                         │  Reviewer approves                │
                         │  ↓                                │
                         │  Merge to main                    │
                         │  ↓                                │
                         │  release.yml runs:                │
                         │    🏷️ v0.0.3 tag created          │
                         │    🐳 Images pushed to ghcr.io    │
                         │    📋 GitHub Release created      │
                         └──────────────────────────────────┘
```

### Day-to-day commands for you

```bash
# 1. Start new work
git checkout main && git pull
git checkout -b feat/my-feature

# 2. Write code, then test locally (same command CI runs)
mvn -B clean verify

# 3. Push and open PR
git push origin feat/my-feature
# Go to GitHub → open PR

# 4. CI runs automatically. Watch it in:
#    GitHub repo → Actions tab → CI → your branch

# 5. After PR is merged, release runs automatically
#    GitHub repo → Actions tab → Release
#    GitHub repo → Releases (left sidebar)
```

---

## 12. Reading the GitHub Actions UI

```
GitHub repo → Actions tab
```

```
[ CI ]  ✅ passed  |  PR: Add user sorting feature  |  2 minutes ago
[ CI ]  ❌ failed  |  PR: Fix typo in README         |  5 minutes ago
[ Release ]  ✅ passed  |  main: Merge fix PR         |  1 hour ago
```

Click any run to see the job breakdown:

```
CI run detail:
  ✅ Build & Test (Java 17)       1m 34s
  ✅ OWASP Dependency Check       3m 12s
  ✅ Docker Build Verification    2m 45s
```

Click a job to see individual steps.  
Click a step to see the raw log output.

**Download artifacts** (test reports, OWASP HTML):  
Scroll to the bottom of any run page → **Artifacts** section → click to download ZIP.

---

## 13. What Happens When a Build Fails

### Tests fail

```
[ERROR] Tests run: 49, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

1. CI marks the PR as ❌ blocked
2. PR cannot be merged
3. Download the `test-reports` artifact → open the XML/HTML in a browser
4. Fix the failing test, push a new commit, CI re-runs

### OWASP CVE found

```
[ERROR] CVE-2024-XXXXX (CVSS 8.5 HIGH)
[ERROR] HIGH or CRITICAL CVE found — see owasp-report artifact
```

1. Download the `owasp-report` artifact → open `dependency-check-report.html`
2. Find the affected dependency and the fixed version
3. Update `pom.xml` → push → CI re-runs
4. OR add a suppression in `.github/owasp-suppressions.xml` if confirmed false positive

### Docker build fails

Usually means:
- Invalid Dockerfile syntax
- Build ARG is missing
- Base image no longer exists

Check the step log for the exact error line.

---

## 14. Secrets — How Credentials Are Stored Safely

**Never put passwords, tokens, or API keys in your code or YAML files.**

GitHub Secrets are encrypted values stored in GitHub. You reference them with `${{ secrets.MY_SECRET }}`.  
They are:
- Never visible in logs (GitHub masks them with `***`)
- Never exported outside the runner
- Scoped to the repository (or organization)

### Secrets used in this project

| Secret | Used in | Purpose |
|--------|---------|---------|
| `GITHUB_TOKEN` | release.yml | Push to `ghcr.io` and create releases. **Auto-provided by GitHub — no setup needed.** |
| `NVD_API_KEY` | ci.yml, release.yml | Faster NVD downloads. Optional but recommended. |

### Adding a secret

Settings → Secrets and variables → Actions → New repository secret

---

## 15. Glossary

| Term | Meaning |
|------|---------|
| **CI** | Continuous Integration — auto build + test on every commit |
| **CD** | Continuous Delivery — auto package + publish after CI passes |
| **Runner** | Virtual machine provided by GitHub to run your workflow |
| **Job** | A group of steps that run on the same runner |
| **Step** | A single action or shell command within a job |
| **Action** | A reusable pre-built step (e.g. `actions/checkout@v4`) |
| **Artifact** | A file saved from a workflow run (test reports, OWASP HTML) |
| **Secret** | An encrypted credential stored in GitHub Settings |
| **ghcr.io** | GitHub Container Registry — where Docker images are pushed |
| **SemVer** | Semantic Versioning — `vMAJOR.MINOR.PATCH` |
| **CVE** | Common Vulnerabilities and Exposures — a numbered security flaw |
| **CVSS** | Score 0–10 measuring CVE severity (≥7 = HIGH in our gate) |
| **NVD** | National Vulnerability Database — the master CVE list |
| **OWASP** | Open Web Application Security Project — maintains dep-check tool |
| **Multi-arch** | Building one image that works on both Intel (amd64) and ARM (arm64) |
| **OCI label** | Metadata burned into a Docker image (source, revision, version) |
| **Branch protection** | GitHub setting that blocks merging a PR until CI passes |
| **Buildx** | Docker's modern multi-platform build tool |
| **concurrency** | GitHub Actions setting to cancel old runs when new commits arrive |
| **`needs`** | Makes one job wait for another job to finish first |

---

## Quick Start Summary

```bash
# As a developer — CI runs automatically when you push
git push origin feat/my-feature   # CI runs on the PR

# As a maintainer — trigger a release manually with a specific version
# GitHub UI: Actions → Release → Run workflow → version: v1.0.0

# Check what version is in production
docker inspect ghcr.io/your-org/hexagonal-scim/app:latest \
  --format '{{ index .Config.Labels "org.opencontainers.image.version" }}'

# Pull a specific version
docker pull ghcr.io/your-org/hexagonal-scim/app:v0.0.3
```

---

*Last updated: 2026-05-10 — covers `ci.yml` and `release.yml` as they exist at v0.0.2.*

