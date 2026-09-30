model "toolshed"

persona Customer
persona Manager

context Order
context Payment

# --- Command pattern: UI -> command -> event ---
slice "Browse Catalog" {
  ui Product Catalog @Customer
  command Place Order
  event Order Placed @Order
}

# --- View pattern: event -> read model -> UI ---
slice "View Open Orders" {
  view Open Orders from "Order Placed"
  ui Order List @Customer
}

slice "Checkout" {
  ui Checkout Screen @Customer
  command Submit Payment
  event Payment Requested @Payment
}

# --- View pattern: a human view of pending payments ---
slice "Manager Review" {
  view Pending Payments from "Payment Requested"
  ui Payment Dashboard @Manager
}

# --- Read model the automation watches, in its own slice ---
slice "Payments To Process" {
  view Payments To Process from "Payment Requested"
}

# --- Automation slice: the processor, the command it triggers, and that command's event ---
slice "Capture Payment" {
  processor Payment Gateway from "Payments To Process"
  command Capture Payment
  event Payment Captured @Payment
}

slice "Show Receipt" {
  view Receipts from "Payment Captured"
  ui Receipt Screen @Customer
}
