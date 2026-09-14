# Runtime Contract Matrix

This matrix records the strongest verified state, not the intended design.

| Contract | Discovered | Normalized | Compiled | Bound | Executable | Persistent | Synchronized | Bedrock-verified | Regression-verified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Block use | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item insert | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item extract | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item move | PARTIAL | PARTIAL | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Fluid fill | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Fluid drain | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Energy receive | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Energy extract | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Menu open | PASS | PASS | PASS | PARTIAL | PARTIAL | OPEN | TRANSPORT | OPEN | PARTIAL |
| Menu button | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Menu property | PARTIAL | PARTIAL | PARTIAL | PARTIAL | OPEN | OPEN | PARTIAL | OPEN | PARTIAL |
| Menu mode | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Entity use | PARTIAL | PARTIAL | PARTIAL | OPEN | OPEN | OPEN | OPEN | OPEN | PARTIAL |
| Entity attack | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Entity mount/dismount | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Machine processing | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Machine persistence | PASS | PASS | PASS | PASS | PARTIAL | OPEN | OPEN | OPEN | PARTIAL |
| Automation route | PASS | PASS | PASS | PARTIAL | PARTIAL | OPEN | PARTIAL | OPEN | PARTIAL |

`TRANSPORT` means a concrete Geyser packet handoff was tested. It never means that an official Bedrock client displayed or applied the result.
