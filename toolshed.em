model "toolshed"

persona Neighbor
persona Shed Manager

context Tools
context Loans
context Holds
context Neighbors

# Timeline order, left to right. Human-triggered State Change slices are ui -> command -> event.
# Read models repeat right after the event that feeds them.

slice "Add Tool" {
  ui Tool Inventory @Shed Manager
  command Add Tool note "slices/add-tool.md"
  event Tool Added @Tools
}
slice "Tool Inventory" {
  view Tool Inventory from "Tool Added" note "slices/tool-inventory.md"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog" {
  view Tool Catalog from "Tool Added" note "slices/tool-catalog.md"
  ui Tool Catalog @Neighbor
}

slice "Register Neighbor" {
  ui Sign Up @Neighbor
  command Register Neighbor note "slices/register-neighbor.md"
  event Neighbor Registered @Neighbors
}
slice "Neighbors" {
  view Neighbors from "Neighbor Registered" note "slices/neighbors.md"
  ui Neighbors @Shed Manager
}
slice "My Account" {
  view My Account from "Neighbor Registered" note "slices/my-account.md"
  ui My Account @Neighbor
}

slice "Check Out Tool" {
  ui Tool Catalog @Neighbor
  command Check Out Tool note "slices/check-out-tool.md"
  event Tool Checked Out @Loans
}
slice "Tool Catalog — checked out" {
  view Tool Catalog again from "Tool Checked Out"
  ui Tool Catalog @Neighbor
}
slice "My Loans" {
  view My Loans from "Tool Checked Out" note "slices/my-loans.md"
  ui My Loans @Neighbor
}
slice "My Holds" {
  view My Holds from "Tool Checked Out" note "slices/my-holds.md"
  ui My Holds @Neighbor
}

slice "Extend Loan" {
  ui My Loans @Neighbor
  command Extend Loan note "slices/extend-loan.md"
  event Loan Extended @Loans
}
slice "My Loans — loan extended" {
  view My Loans again from "Loan Extended"
  ui My Loans @Neighbor
}
slice "Tool Catalog — loan extended" {
  view Tool Catalog again from "Loan Extended"
  ui Tool Catalog @Neighbor
}

slice "Place Hold" {
  ui Tool Catalog @Neighbor
  command Place Hold note "slices/place-hold.md"
  event Hold Placed @Holds
}
slice "Tool Catalog — hold placed" {
  view Tool Catalog again from "Hold Placed"
  ui Tool Catalog @Neighbor
}
slice "My Holds — hold placed" {
  view My Holds again from "Hold Placed"
  ui My Holds @Neighbor
}
slice "My Loans — hold placed" {
  view My Loans again from "Hold Placed"
  ui My Loans @Neighbor
}

slice "Cancel Hold" {
  ui My Holds @Neighbor
  command Cancel Hold note "slices/cancel-hold.md"
  event Hold Cancelled @Holds
}
slice "My Holds — hold cancelled" {
  view My Holds again from "Hold Cancelled"
  ui My Holds @Neighbor
}
slice "Tool Catalog — hold cancelled" {
  view Tool Catalog again from "Hold Cancelled"
  ui Tool Catalog @Neighbor
}
slice "My Loans — hold cancelled" {
  view My Loans again from "Hold Cancelled"
  ui My Loans @Neighbor
}

slice "Overdue Loans To Notify" {
  view Overdue Loans To Notify from "Tool Added", "Neighbor Registered", "Tool Checked Out", "Loan Extended" note "slices/overdue-loans-to-notify.md"
}

# Internally triggered translation: the gateway watches the to-do view and hands notices to the provider.
slice "Send Overdue Notice" {
  translation Overdue Notice Gateway from "Overdue Loans To Notify" note "slices/send-overdue-notice.md"
  command Send Overdue Notice
  event Overdue Notice Sent loops-to "Overdue Loans To Notify" @Loans
}
slice "My Loans — overdue notice sent" {
  view My Loans again from "Overdue Notice Sent"
  ui My Loans @Neighbor
}

slice "Record Overdue Notice Failure" {
  translation Email Provider Failure Report note "slices/record-overdue-notice-failure.md"
  command Record Overdue Notice Failure
  event Overdue Notice Failed @Loans
}
slice "Undelivered Notices" {
  view Undelivered Notices from "Overdue Notice Failed" note "slices/undelivered-notices.md"
  ui Undelivered Notices @Shed Manager
}

slice "Return Tool" {
  ui My Loans @Neighbor
  command Return Tool note "slices/return-tool.md"
  event Tool Returned loops-to "Overdue Loans To Notify" @Loans
}
slice "Tool Catalog — tool returned" {
  view Tool Catalog again from "Tool Returned"
  ui Tool Catalog @Neighbor
}
slice "My Loans — tool returned" {
  view My Loans again from "Tool Returned"
  ui My Loans @Neighbor
}
slice "Neighbors To Flag" {
  view Neighbors To Flag from "Tool Returned" note "slices/neighbors-to-flag.md"
}

slice "Holds To Fulfill" {
  view Holds To Fulfill from "Hold Placed", "Tool Returned", "Hold Cancelled", "Tool Checked Out" note "slices/holds-to-fulfill.md"
}

slice "Fulfill Hold" {
  processor Hold Fulfiller from "Holds To Fulfill" note "slices/fulfill-hold.md"
  command Fulfill Hold
  event Hold Fulfilled loops-to "Holds To Fulfill" @Holds
}
slice "Tool Catalog — hold fulfilled" {
  view Tool Catalog again from "Hold Fulfilled"
  ui Tool Catalog @Neighbor
}
slice "My Holds — hold fulfilled" {
  view My Holds again from "Hold Fulfilled"
  ui My Holds @Neighbor
}

slice "Pickup Notices To Send" {
  view Pickup Notices To Send from "Hold Fulfilled", "Neighbor Registered", "Tool Added", "Hold Cancelled" note "slices/pickup-notices-to-send.md"
}

slice "Send Pickup Notice" {
  translation Pickup Notice Gateway from "Pickup Notices To Send" note "slices/send-pickup-notice.md"
  command Send Pickup Notice
  event Pickup Notice Sent loops-to "Pickup Notices To Send" @Holds
}
slice "My Holds — pickup notice sent" {
  view My Holds again from "Pickup Notice Sent"
  ui My Holds @Neighbor
}

slice "Pickups To Expire" {
  view Pickups To Expire from "Hold Fulfilled", "Tool Checked Out", "Hold Cancelled" note "slices/pickups-to-expire.md"
}

slice "Expire Hold" {
  processor Pickup Expirer from "Pickups To Expire" note "slices/expire-hold.md"
  command Expire Hold
  event Hold Expired loops-to "Holds To Fulfill", "Pickups To Expire" @Holds
}
slice "My Holds — hold expired" {
  view My Holds again from "Hold Expired"
  ui My Holds @Neighbor
}
slice "Tool Catalog — hold expired" {
  view Tool Catalog again from "Hold Expired"
  ui Tool Catalog @Neighbor
}

slice "Flag Neighbor" {
  processor Late Return Flagger from "Neighbors To Flag" note "slices/flag-neighbor.md"
  command Flag Neighbor
  event Neighbor Flagged loops-to "Neighbors To Flag" @Neighbors
}
slice "Flagged Neighbors" {
  view Flagged Neighbors from "Neighbor Flagged" note "slices/flagged-neighbors.md"
  ui Flagged Neighbors @Shed Manager
}

slice "Suspend Neighbor" {
  ui Flagged Neighbors @Shed Manager
  command Suspend Neighbor note "slices/suspend-neighbor.md"
  event Neighbor Suspended @Neighbors
}
slice "Flagged Neighbors — neighbor suspended" {
  view Flagged Neighbors again from "Neighbor Suspended"
  ui Flagged Neighbors @Shed Manager
}
slice "Suspended Neighbors" {
  view Suspended Neighbors from "Neighbor Suspended" note "slices/suspended-neighbors.md"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor suspended" {
  view Neighbors again from "Neighbor Suspended"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor suspended" {
  view My Account again from "Neighbor Suspended"
  ui My Account @Neighbor
}

slice "Reinstate Neighbor" {
  ui Suspended Neighbors @Shed Manager
  command Reinstate Neighbor note "slices/reinstate-neighbor.md"
  event Neighbor Reinstated loops-to "Neighbors To Flag" @Neighbors
}
slice "Suspended Neighbors — neighbor reinstated" {
  view Suspended Neighbors again from "Neighbor Reinstated"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor reinstated" {
  view Neighbors again from "Neighbor Reinstated"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor reinstated" {
  view My Account again from "Neighbor Reinstated"
  ui My Account @Neighbor
}

slice "Retire Tool" {
  ui Tool Inventory @Shed Manager
  command Retire Tool note "slices/retire-tool.md"
  event Tool Retired loops-to "Holds To Fulfill", "Pickups To Expire", "Pickup Notices To Send" @Tools
}
slice "Tool Inventory — tool retired" {
  view Tool Inventory again from "Tool Retired"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool retired" {
  view Tool Catalog again from "Tool Retired"
  ui Tool Catalog @Neighbor
}
slice "My Holds — tool retired" {
  view My Holds again from "Tool Retired"
  ui My Holds @Neighbor
}
slice "My Loans — tool retired" {
  view My Loans again from "Tool Retired"
  ui My Loans @Neighbor
}

slice "Close Account" {
  ui My Account @Neighbor
  command Close Account note "slices/close-account.md"
  event Neighbor Account Closed loops-to "Holds To Fulfill", "Pickups To Expire", "Pickup Notices To Send", "Neighbors To Flag" @Neighbors
}
slice "My Account — account closed" {
  view My Account again from "Neighbor Account Closed"
  ui My Account @Neighbor
}
slice "Neighbors — account closed" {
  view Neighbors again from "Neighbor Account Closed"
  ui Neighbors @Shed Manager
}
slice "My Holds — account closed" {
  view My Holds again from "Neighbor Account Closed"
  ui My Holds @Neighbor
}
slice "Tool Catalog — account closed" {
  view Tool Catalog again from "Neighbor Account Closed"
  ui Tool Catalog @Neighbor
}
slice "My Loans — account closed" {
  view My Loans again from "Neighbor Account Closed"
  ui My Loans @Neighbor
}
