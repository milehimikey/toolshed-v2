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

slice "Overdue Loans To Notify" {
  view Overdue Loans To Notify from "Tool Checked Out", "Loan Extended" note "slices/overdue-loans-to-notify.md"
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
  view Holds To Fulfill from "Hold Placed", "Tool Returned" note "slices/holds-to-fulfill.md"
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

slice "Reinstate Neighbor" {
  ui Suspended Neighbors @Shed Manager
  command Reinstate Neighbor note "slices/reinstate-neighbor.md"
  event Neighbor Reinstated @Neighbors
}
slice "Suspended Neighbors — neighbor reinstated" {
  view Suspended Neighbors again from "Neighbor Reinstated"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor reinstated" {
  view Neighbors again from "Neighbor Reinstated"
  ui Neighbors @Shed Manager
}

slice "Retire Tool" {
  ui Tool Inventory @Shed Manager
  command Retire Tool note "slices/retire-tool.md"
  event Tool Retired @Tools
}
slice "Tool Inventory — tool retired" {
  view Tool Inventory again from "Tool Retired"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool retired" {
  view Tool Catalog again from "Tool Retired"
  ui Tool Catalog @Neighbor
}

slice "Close Account" {
  ui My Account @Neighbor
  command Close Account note "slices/close-account.md"
  event Neighbor Account Closed @Neighbors
}
slice "My Account — account closed" {
  view My Account again from "Neighbor Account Closed"
  ui My Account @Neighbor
}
slice "Neighbors — account closed" {
  view Neighbors again from "Neighbor Account Closed"
  ui Neighbors @Shed Manager
}
