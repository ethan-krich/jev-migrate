"""Example mapping customisation, used by run_example.py and `python -m transpiler --mappings`.

Mappings are ordinary Python data: start from the defaults and change what you need.
"""

from transpiler import MappingRegistry


def configure(mappings: MappingRegistry) -> MappingRegistry:
    # Inspect: every rule has a stable id.
    for rule in mappings.for_source("MOVE"):
        print(f"  {rule.id}: {rule.source} when {dict(rule.when)} -> {rule.handler}")

    # Keep CALL deterministic only for programs inside the project; anything else (dynamic or
    # external calls) already becomes a decision. Here we additionally ask for review of every
    # CALL that uses USING, by marking it unresolved at a higher priority.
    mappings.add("CALL", strategy="unresolved", when={"using": True}, priority=200, id="example.review-call-using")

    # Route calls to an external program through a user callback in phase 2.
    mappings.add("CALL", strategy="callback", when={"program": "EXTPROG"}, priority=300,
                 script="./callbacks.py", function="resolve_call", id="example.extprog-callback")
    return mappings
