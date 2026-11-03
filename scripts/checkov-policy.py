#!/usr/bin/env python3
"""Print the fleet policy regenerated from the pinned Checkov --list output."""

import argparse
import re
from pathlib import Path


# These exclusions preserve capabilities that the fleet contract deliberately permits.
EXCEPTIONS = {
    "CKV2_AWS_28": "Provisioning a WAF is an architecture decision.",
    "CKV2_AWS_29": "Provisioning a WAF is an architecture decision.",
    "CKV2_AWS_33": "Provisioning a WAF is an architecture decision.",
    "CKV_AZURE_120": "Provisioning a WAF is an architecture decision.",
    "CKV_AZURE_121": "Provisioning a WAF is an architecture decision.",
    "CKV_AWS_257": "The fleet merges agent work without mandatory human review.",
    "CKV_DOCKER_1": "An SSH endpoint is a workload choice, not proof of insecure access.",
    "CKV_DOCKER_2": "A CLI or batch container need not expose a health endpoint.",
    "CKV_DOCKER_3": "A base image can already declare its non-root user.",
    "CKV_DOCKER_4": "ADD also supports legitimate local archive extraction.",
    "CKV_DOCKER_7": "Airness owns container tag and digest validation.",
    "CKV_GHA_5": "Airness permits signing mechanisms other than Cosign.",
    "CKV_GHA_6": "A particular SBOM attestation product is not mandatory.",
    "CKV_GHA_7": "Governed release workflows may declare manual inputs.",
    "CKV_K8S_15": "A pinned image permits cached pulls and disconnected deployments.",
    "CKV_K8S_21": "Namespace allocation is a deployment choice.",
    "CKV_K8S_35": "Applications may receive secrets through environment variables.",
    "CKV_K8S_40": "A base image may declare a non-root UID below the arbitrary floor.",
    "CKV_K8S_43": "Airness permits explicit version tags as well as digests.",
    "CKV_AWS_39": "A secured and restricted public control-plane endpoint is valid.",
    "CKV_AWS_88": "Public compute is valid when access is governed.",
    "CKV_AWS_164": "A file transfer service may be intentionally public.",
    "CKV_AWS_301": "A function may intentionally expose a public application endpoint.",
    "CKV_AWS_333": "Public application tasks are a deployment choice.",
    "CKV_AZURE_221": "A function may intentionally expose a public application endpoint.",
    "CKV_AZURE_222": "A web application may intentionally have a public endpoint.",
    "CKV_GCP_40": "Public compute is valid when access is governed.",
    "CKV_GCP_102": "A service may intentionally expose a public application endpoint.",
    "CKV_GCP_107": "A function may intentionally expose a public application endpoint.",
}
for number in range(8, 14):
    EXCEPTIONS[f"CKV_K8S_{number}"] = "Probes and resource sizing depend on the workload."

# These requirements are a deployment's or a product's decisions rather than portable security controls.
EXCEPTIONS.update({
    "CKV_AWS_374": "Restricting an audience by country is a product decision.",
    "CKV_AWS_305": "An application origin or an edge function may answer the root path itself.",
    "CKV_AWS_259": "HSTS subdomain scope and preload depend on who owns the parent domain.",
    "CKV_AWS_378": "TLS may end at the load balancer, with the hop behind it inside a private network.",
    "CKV2_AWS_47": "Blocking anonymising networks is a product decision; CKV_AWS_192 judges the Log4j rules.",
    "CKV2_AWS_62": "Event notifications are an integration choice.",
    "CKV2_AWS_34": "A parameter may hold non-secret configuration; secrets belong in a secret store.",
    "CKV2_AWS_57": "Automatic rotation needs a rotation function, which only some kinds of secret have.",
    "CKV_AWS_118": "Tracing and performance monitoring depend on the workload.",
    "CKV_AWS_284": "Tracing and performance monitoring depend on the workload.",
    "CKV2_AWS_61": "Retention depends on the data and the deployment.",
    "CKV_AWS_338": "Retention depends on the data and the deployment.",
    "CKV_AWS_336": "Whether a root filesystem can be read-only depends on where the application writes.",
    "CKV_K8S_22": "Whether a root filesystem can be read-only depends on where the application writes.",
})
# A logging rule demands one log source; which sources a deployment keeps is its logging design.
for identifier in ("CKV_AWS_18", "CKV_AWS_86", "CKV_AWS_91", "CKV_AWS_129", "CKV_AWS_285",
                   "CKV2_AWS_11", "CKV2_AWS_30"):
    EXCEPTIONS[identifier] = "Which log sources a deployment keeps is part of its logging design."

# Match the policy's requirement, not the resource's name. For example a WAF rule's
# security checks stay enabled; only the requirement to provision a WAF is omitted.
REQUIREMENTS = (
    (r"approving reviews|CODEOWNER reviews|requires? .*reviews|review approvals",
     "The fleet merges agent work without mandatory human review."),
    (r"customer.managed|\bCMK\b|encrypted.*(?:using|with|by) (?:a )?KMS",
     "Encryption does not require a particular key ownership model."),
    (r"multi.az|high.availability|cross.region replication|use replication|standard replication|origin failover",
     "Availability and replication depend on the deployment."),
    (r"(?:have|has|with|is|are|be) (?:a )?WAF enabled|WAF (?:is )?enabled|associated with.*WAF",
     "Provisioning a WAF is an architecture decision."),
    (r"(?:defender|security center|guardduty).*enabled|enable.*(?:defender|guardduty)",
     "A paid security service is not mandatory for every deployment."),
    (r"backup (?:policy|configuration|retention)|automatic backup|recovery \(backup\)|are backed up",
     "Backup provisioning and retention depend on the data and deployment."),
    (r"deletion protection|prevent_destroy|delete protection|final snapshot",
     "Disposable environments and intentionally replaceable resources remain valid."),
)


def policies(text):
    """Read unique rule IDs from Checkov's registry listing, not its website index."""
    result = {}
    for line in text.splitlines():
        columns = [value.strip() for value in line.split("|")]
        if len(columns) > 6 and re.fullmatch(r"CKV2?_[A-Z0-9_]+", columns[2]):
            result[columns[2]] = columns[5]
    if not result:
        raise ValueError("Checkov supplied no rule inventory")
    return result


def render(text):
    """Apply the central scope decisions, naming every individual rule in the result."""
    lines = ["# Generated by scripts/checkov-policy.py from Checkov 3.4.3 --list.",
             "# Rule ID\tDisposition\tReason\tUpstream policy"]
    for identifier, title in sorted(policies(text).items()):
        reason = EXCEPTIONS.get(identifier, "")
        for expression, explanation in REQUIREMENTS:
            if not reason and re.search(expression, title, re.IGNORECASE):
                reason = explanation
        disposition = "exclude" if reason else "enable"
        title = title.encode("ascii", "replace").decode("ascii")
        lines.append("\t".join((identifier, disposition, reason or "Portable security requirement.", title)))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("listing", type=Path)
    arguments = parser.parse_args()
    print(render(arguments.listing.read_text(encoding="utf-8")), end="")


if __name__ == "__main__":
    main()
