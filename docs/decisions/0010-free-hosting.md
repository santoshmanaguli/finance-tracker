# ADR-0010: Free-tier hosting and tooling

- **Status:** Proposed (needs the owner to create accounts)
- **Date:** 2026-11-18

## Context

The project must cost (close to) nothing to develop and run, and it should teach real DevOps: Linux servers, containers, CI/CD, infrastructure as code, Kubernetes, GitOps and observability. Managed platforms that hide all of that (one-click PaaS) defeat the learning goal.

## Decision

| Need | Choice | Notes |
|---|---|---|
| Code, CI/CD, image registry | **GitHub + GitHub Actions + GHCR** | Private repos get 2,000 free Actions minutes a month |
| Server | **Oracle Cloud "Always Free"** ARM VM (up to 4 OCPU / 24 GB) | See "Oracle's two free offers" below |
| Fallback server | **An old laptop or PC at home** + Cloudflare Tunnel, or a GCP `e2-micro` (always free, 1 GB RAM, US regions only, too small for k3s) | |
| API domain + HTTPS | **DuckDNS** free subdomain (`<name>.duckdns.org`) → VM IP; **Caddy** obtains a Let's Encrypt certificate automatically | Stable and free; needs ports 80/443 open in the VM firewall |
| Web app (iPhone) hosting | **Cloudflare Pages** → `<name>.pages.dev` | Free, global CDN |
| Android APK build | GitHub Actions | |
| Database | PostgreSQL in a container on the VM, plus nightly `pg_dump` to free object storage | Avoids free-tier databases that sleep or pause |
| Kubernetes | **k3s** on the same VM | |
| Observability | Grafana Cloud free tier or self-hosted Prometheus/Loki/Grafana; **Healthchecks.io** free plan for the dead-man's-switch alert | |
| LLM | **Ollama** on the VM (small models, CPU inference) or free API tiers | Self-hosting keeps friends' data private |

### Oracle's two free offers (often confused)

1. **Free Trial:** USD 300 of credits, usable for **30 days**, on almost any service.
2. **Always Free resources:** a fixed set (including the ARM VM above, 200 GB block storage and object storage) that **stay free after the 30 days end**, as long as the account stays within those limits.

The "30 days" on Oracle's website refers to the trial credits, not the Always Free tier. Verify on Oracle's "Always Free Resources" page when signing up, because terms change. Known risks:
- ARM capacity in Indian regions is often "out of capacity"; retry or choose another region.
- **Idle Always Free instances may be reclaimed.** Real usage plus monitoring helps.
- Upgrading to Pay-As-You-Go avoids reclamation and stays free within the limits, but then a mistake *can* cost money. Set a budget alert first.

### Why not a "free domain" from Vercel?

`*.vercel.app` (Vercel) and `*.pages.dev` (Cloudflare Pages) are free **only for apps hosted on those platforms**: good for the web app, not for our own API server. Cloudflare's stable named tunnels need a domain registered in Cloudflare; its free "quick tunnels" give a **random URL that changes on every restart**, which would break the iPhone Shortcut. DuckDNS gives a stable free name for the API.

## Consequences

- ➕ ₹0 running cost; every layer is hands-on DevOps learning.
- ➖ Self-managed means we own backups, patching and uptime; that is the point, but it is real work.
- ➖ Free tiers change and can disappear. Everything will be in Terraform, Docker and Helm, so moving to another provider is a re-apply, not a rebuild.
- Optional later: a paid domain (about ₹500–900/year) for a nicer URL and Cloudflare named tunnels (no open ports).
