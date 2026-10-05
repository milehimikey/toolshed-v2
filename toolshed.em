model "toolshed"

persona Neighbor
persona Shed Manager

# Timeline order, left to right. Human-triggered State Change slices are ui -> command -> event.
# Read models repeat right after the event that feeds them. Events stay on the default lane.

slice "Add Tool" {
  ui Tool Inventory @Shed Manager
  command Add Tool
  event Tool Added
}
slice "Tool Inventory — tool added" {
  view Tool Inventory from "Tool Added"
  ui Tool Inventory @Shed Manager
}
slice "Tool Catalog — tool added" {
  view Tool Catalog from "Tool Added"
  ui Tool Catalog @Neighbor
}

slice "Register Neighbor" {
  ui Sign Up @Neighbor
  command Register Neighbor
  event Neighbor Registered
}
slice "Neighbors — neighbor registered" {
  view Neighbors from "Neighbor Registered"
  ui Neighbors @Shed Manager
}
slice "My Account — neighbor registered" {
  view My Account from "Neighbor Registered"
  ui My Account @Neighbor
}

slice "Check Out Tool" {
  ui Tool Catalog @Neighbor
  command Check Out Tool
  event Tool Checked Out
}
slice "Tool Catalog — checked out" {
  view Tool Catalog again from "Tool Checked Out"
  ui Tool Catalog @Neighbor
}
slice "My Loans — checked out" {
  view My Loans from "Tool Checked Out"
  ui My Loans @Neighbor
}
slice "My Holds — checked out by holder" {
  view My Holds from "Tool Checked Out"
  ui My Holds @Neighbor
}

slice "Extend Loan" {
  ui My Loans @Neighbor
  command Extend Loan
  event Loan Extended
}
slice "My Loans — loan extended" {
  view My Loans again from "Loan Extended"
  ui My Loans @Neighbor
}

slice "Place Hold" {
  ui Tool Catalog @Neighbor
  command Place Hold
  event Hold Placed
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
  view Overdue Loans To Notify from "Tool Checked Out", "Loan Extended"
}

# Automation: the to-do view sits in an earlier slice; the processor, its command and the event share this slice.
slice "Send Overdue Notice" {
  processor Overdue Notifier from "Overdue Loans To Notify"
  command Send Overdue Notice
  event Overdue Notice Sent loops-to "Overdue Loans To Notify"
}
slice "My Loans — overdue notice sent" {
  view My Loans again from "Overdue Notice Sent"
  ui My Loans @Neighbor
}

slice "Return Tool" {
  ui My Loans @Neighbor
  command Return Tool
  event Tool Returned loops-to "Overdue Loans To Notify"
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
  view Neighbors To Flag from "Tool Returned"
}

slice "Holds To Fulfill" {
  view Holds To Fulfill from "Hold Placed", "Tool Returned"
}

slice "Fulfill Hold" {
  processor Hold Fulfiller from "Holds To Fulfill"
  command Fulfill Hold
  event Hold Fulfilled loops-to "Holds To Fulfill"
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
  processor Late Return Flagger from "Neighbors To Flag"
  command Flag Neighbor
  event Neighbor Flagged loops-to "Neighbors To Flag"
}
slice "Flagged Neighbors — neighbor flagged" {
  view Flagged Neighbors from "Neighbor Flagged"
  ui Flagged Neighbors @Shed Manager
}

slice "Suspend Neighbor" {
  ui Flagged Neighbors @Shed Manager
  command Suspend Neighbor
  event Neighbor Suspended
}
slice "Flagged Neighbors — neighbor suspended" {
  view Flagged Neighbors again from "Neighbor Suspended"
  ui Flagged Neighbors @Shed Manager
}
slice "Suspended Neighbors — neighbor suspended" {
  view Suspended Neighbors from "Neighbor Suspended"
  ui Suspended Neighbors @Shed Manager
}
slice "Neighbors — neighbor suspended" {
  view Neighbors again from "Neighbor Suspended"
  ui Neighbors @Shed Manager
}

slice "Reinstate Neighbor" {
  ui Suspended Neighbors @Shed Manager
  command Reinstate Neighbor
  event Neighbor Reinstated
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
  command Retire Tool
  event Tool Retired
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
  command Close Account
  event Neighbor Account Closed
}
slice "My Account — account closed" {
  view My Account again from "Neighbor Account Closed"
  ui My Account @Neighbor
}
slice "Neighbors — account closed" {
  view Neighbors again from "Neighbor Account Closed"
  ui Neighbors @Shed Manager
}
