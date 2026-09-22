# Physical-device acceptance

Record phone model, firmware, ambient conditions, charging state, brightness and workload. These checks cannot be marked passed by a desktop build.

1. Install on API 29+. Confirm detected hardware matches Android settings. Unknown secondary chip must not be called absent.
2. Start observation. Check battery percentage against Android. Missing temperature/headroom/current must say unavailable and not crash.
3. Record two minutes under a steady ordinary workload. Pin a two-minute forecast, wait for its real result, inspect the error. Repeat with five minutes of history for a five-minute horizon.
4. Pin, then record a manual intervention. Verify that comparison is marked changed-context and is excluded from calibration.
5. Change workload or charging state. New forecasts must wait for fresh history.
6. Turn the screen off and return. Observation reduces; gaps invalidate affected forecasts.
7. Stop from notification; restart. Deny notification permission and use in-app Stop. Rotate the phone and switch tabs during a session; there must be one collector only.
8. Stop and reopen; verify DNA and history persist. Force-stop during observation: restart should report interruption, not fabricate pending outcomes.
9. Export JSON; cancel and retry export. Stop and delete history; verify DNA resets.
10. Try another Android phone and confirm standard fallback remains functional.

## Overhead experiment

Run comparable ordinary workloads with observation off and on, matching brightness, duration, charging, starting temperature and environment. Repeat in both orders. Use an independent profiler/Perfetto setup where available. Record SOVARIX CPU/PSS alongside whole-device battery/thermal changes; do not confuse correlation with attributable app cost. Only report cross-app FPS if the chosen measurement tool actually exposes it.

Governor limits are configurable engineering defaults, not manufacturer limits. Tests use synthetic thermal states to validate critical-state behavior. Do not intentionally overheat a phone for the demonstration; use ordinary workloads and stop when Android warns.
