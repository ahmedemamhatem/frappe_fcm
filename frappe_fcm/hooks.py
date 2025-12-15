app_name = "frappe_fcm"
app_title = "Frappe FCM"
app_publisher = "Frappe FCM Contributors"
app_description = "Firebase Cloud Messaging (FCM) Push Notifications for Frappe"
app_email = "hello@frappe.io"
app_license = "MIT"
app_version = "1.0.0"

# Required Apps
required_apps = ["frappe"]

# App includes in <head>
# ------------------
# include js, css files in header of desk.html
# app_include_css = "/assets/frappe_fcm/css/frappe_fcm.css"
# app_include_js = "/assets/frappe_fcm/js/frappe_fcm.js"

# include js, css files in header of web template
# web_include_css = "/assets/frappe_fcm/css/frappe_fcm.css"
# web_include_js = "/assets/frappe_fcm/js/frappe_fcm.js"

# include custom scss in every website theme (without signing in)
# website_theme_scss = "frappe_fcm/public/scss/website"

# include js in doctype views
# doctype_js = {
#     "doctype": "public/js/doctype.js"
# }

# Home Pages
# ----------
# application home page (will override Website Settings)
# home_page = "login"

# website user home page (by Role)
# role_home_page = {
#     "Role": "home_page"
# }

# Generators
# ----------
# automatically create page for each record of this doctype
# website_generators = ["Web Page"]

# Jinja
# ----------
# add methods and filters to jinja environment
# jinja = {
#     "methods": "frappe_fcm.utils.jinja_methods",
#     "filters": "frappe_fcm.utils.jinja_filters"
# }

# Installation
# ------------
# before_install = "frappe_fcm.install.before_install"
after_install = "frappe_fcm.install.after_install"
after_migrate = "frappe_fcm.install.after_migrate"

# Uninstall
# ------------
# before_uninstall = "frappe_fcm.uninstall.before_uninstall"
# after_uninstall = "frappe_fcm.uninstall.after_uninstall"

# Desk Notifications
# ------------------
# See frappe.core.notifications.get_notification_config
# notification_config = "frappe_fcm.notifications.get_notification_config"

# Permissions
# -----------
# Permissions evaluated in scripted ways
# permission_query_conditions = {
#     "Event": "frappe.desk.doctype.event.event.get_permission_query_conditions",
# }

# has_permission = {
#     "Event": "frappe.desk.doctype.event.event.has_permission",
# }

# DocType Class
# ---------------
# Override standard doctype classes
# override_doctype_class = {
#     "ToDo": "custom_app.overrides.CustomToDo"
# }

# Document Events
# ---------------
# Hook on document methods and events
doc_events = {
    # Hook into Frappe's Notification Log to auto-send FCM
    # This triggers when any Frappe Notification (System/Email) is sent
    "Notification Log": {
        "after_insert": "frappe_fcm.fcm.frappe_integration.on_notification_log_insert"
    }
}

# Scheduled Tasks
# ---------------
scheduler_events = {
    # Clean up old notification logs (optional)
    # "daily": [
    #     "frappe_fcm.fcm.cleanup.cleanup_old_logs"
    # ],
}

# Testing
# -------
# before_tests = "frappe_fcm.install.before_tests"

# Overriding Methods
# ------------------------------
# override_whitelisted_methods = {
#     "frappe.desk.doctype.event.event.get_events": "frappe_fcm.event.get_events"
# }

# Each overriding function accepts a `data` argument; this argument has an attribute
# `source_name` which is the name of the original method
# override_doctype_dashboards = {
#     "Task": "frappe_fcm.task.get_dashboard_data"
# }

# Exempt DocTypes from restricted permissions
# -------------------------------------------
# exempt_doctypes = ["DocType", "DocField"]

# User Data Protection
# --------------------
# user_data_fields = [
#     {
#         "doctype": "{doctype_1}",
#         "filter_by": "{filter_by}",
#         "redact_fields": ["{field_1}", "{field_2}"],
#         "partial": 1,
#     },
# ]

# Authentication and authorization
# --------------------------------
# auth_hooks = [
#     "frappe_fcm.auth.validate"
# ]

# Fixtures
# --------
fixtures = [
    {
        "doctype": "Custom Field",
        "filters": [["module", "=", "FCM"]]
    }
]
