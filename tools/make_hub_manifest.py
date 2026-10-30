#!/usr/bin/env python3
"""Print a Plugin Hub marker from real repository and commit arguments."""
import argparse
import re

parser = argparse.ArgumentParser()
parser.add_argument("--repository", required=True, help="Public HTTPS GitHub clone URL ending .git")
parser.add_argument("--commit", required=True, help="Actual full 40-character plugin commit SHA")
args = parser.parse_args()
if not re.fullmatch(r"https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.git", args.repository):
    parser.error("Expected https://github.com/OWNER/REPOSITORY.git")
if not re.fullmatch(r"[0-9a-fA-F]{40}", args.commit) or len(set(args.commit)) == 1:
    parser.error("Use the actual full 40-character commit SHA, not a placeholder")
print("repository=" + args.repository)
print("commit=" + args.commit.lower())
