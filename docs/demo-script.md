# Demo recording script

Target about three minutes, following the organizer's guidance. Before recording, start the stack, enable the payment flag in the isolated Flagsmith UI, and establish healthy traffic. Choose a viewport that shows both cohort bars and the right-hand panels. Avoid recording credentials or environment files.

Select **Healthy baseline**, start traffic, and wait for at least 30 healthy requests with both cohorts present before selecting **Payment regression**. Keep traffic running through approval and recovery. The **Requests** card counts checkouts in the last 20 seconds. The Overview bars show checkout pass rates. The incident evidence shows server error rates.

| Approximate time | Show |
| --- | --- |
| 0:00–0:20 | Release overview with healthy requests. Explain that a payment rollout can break its exposed cohort while stable checkout still works. |
| 0:20–0:45 | Flagsmith's actual 40% experimental allocation and ReleaseGuard flag inventory. Trigger Payment regression in Incident Lab. |
| 0:45–1:20 | Open the detected incident, compare sample counts, errors and P95, then run the investigation. Show the recorded read-only tools and evidence. |
| 1:20–1:50 | Review the exact environment and enabled-to-disabled change. Click Approve rollback. Show Flagsmith readback and fresh stable traffic recovery. |
| 1:50–2:35 | Re-enable the isolated flag manually and show the shared dependency scenario with a new window. Show elevated errors in both cohorts and the absence of a rollback proposal. |
| 2:35–2:55 | Show the audit trail and summarize the role of Flagsmith and human approval. |

You may cut waiting time between investigation and recovery screens. Keep the state transitions and evidence visible. The working demo and actual video are separate submission items. [Toggle Wars judging guidance](https://www.wemakedevs.org/blogs/how-to-win-toggle-wars)
