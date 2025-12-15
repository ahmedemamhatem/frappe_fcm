// Copyright (c) 2025, Frappe FCM Contributors
// For license information, please see license.txt

frappe.ui.form.on("FCM Settings", {
    refresh: function(frm) {
        // Clear any existing buttons first
        frm.page.clear_primary_action();
        frm.page.clear_secondary_action();

        // Add primary button at top of page only
        frm.page.set_primary_action(__("Test FCM Connection"), function() {
            test_fcm_connection(frm, true);
        }, "fa fa-check");

        // Auto-test connection on page load (without freeze)
        test_fcm_connection(frm, false);
    }
});

function test_fcm_connection(frm, show_freeze) {
    // Show loading state
    if (frm.fields_dict.fcm_connection_status && frm.fields_dict.fcm_connection_status.$wrapper) {
        frm.fields_dict.fcm_connection_status.$wrapper.html(`
            <div style="background: #e9ecef; border: 1px solid #ced4da; border-radius: 8px; padding: 20px; margin: 10px 0;">
                <div style="display: flex; align-items: center;">
                    <span style="font-size: 24px; margin-right: 15px;">&#8987;</span>
                    <div>
                        <h4 style="color: #495057; font-size: 18px; margin: 0;">Testing Connection...</h4>
                    </div>
                </div>
            </div>
        `);
    }

    frappe.call({
        method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.test_fcm_connection",
        freeze: show_freeze,
        freeze_message: __("Testing FCM connection..."),
        callback: function(r) {
            if (r && r.message) {
                // Update the status display in the page
                update_status_display(frm, r.message);

                // Show alert only when manually triggered
                if (show_freeze) {
                    if (r.message.success) {
                        frappe.show_alert({
                            message: __("Connection successful!"),
                            indicator: "green"
                        }, 3);
                    } else {
                        frappe.show_alert({
                            message: r.message.message || __("Connection failed"),
                            indicator: "red"
                        }, 5);
                    }
                }
            } else {
                update_status_display(frm, {
                    success: false,
                    message: __("No response from server")
                });
            }
        },
        error: function(r) {
            update_status_display(frm, {
                success: false,
                message: __("Error testing connection. Please check your configuration.")
            });
        }
    });
}

function update_status_display(frm, result) {
    let status_html = "";

    if (result) {
        if (result.success) {
            status_html = `
                <div style="background: #d4edda; border: 1px solid #c3e6cb; border-radius: 8px; padding: 20px; margin: 10px 0;">
                    <div style="display: flex; align-items: flex-start;">
                        <span style="font-size: 32px; margin-right: 15px; color: #28a745;">&#10004;</span>
                        <div>
                            <h4 style="color: #155724; font-size: 18px; margin: 0 0 8px 0;">Connection Successful</h4>
                            <p style="margin: 0 0 5px 0; color: #155724;">${result.message || 'FCM is configured correctly'}</p>
                            ${result.project_id ? `<p style="margin: 5px 0 0 0; color: #666;"><strong>Project ID:</strong> ${result.project_id}</p>` : ""}
                            ${result.api_type ? `<p style="margin: 3px 0 0 0; color: #666;"><strong>API Type:</strong> ${result.api_type}</p>` : ""}
                        </div>
                    </div>
                </div>`;
        } else {
            status_html = `
                <div style="background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 8px; padding: 20px; margin: 10px 0;">
                    <div style="display: flex; align-items: flex-start;">
                        <span style="font-size: 32px; margin-right: 15px; color: #dc3545;">&#10008;</span>
                        <div>
                            <h4 style="color: #721c24; font-size: 18px; margin: 0 0 8px 0;">Connection Failed</h4>
                            <p style="margin: 0; color: #721c24;">${result.message || 'Please check your FCM configuration'}</p>
                        </div>
                    </div>
                </div>`;
        }
    } else {
        status_html = `
            <div style="background: #fff3cd; border: 1px solid #ffc107; border-radius: 8px; padding: 20px; margin: 10px 0;">
                <div style="display: flex; align-items: flex-start;">
                    <span style="font-size: 32px; margin-right: 15px; color: #856404;">&#9888;</span>
                    <div>
                        <h4 style="color: #856404; font-size: 18px; margin: 0 0 8px 0;">Not Tested</h4>
                        <p style="margin: 0; color: #856404;">Click the <strong>"Test FCM Connection"</strong> button in the top right to verify your configuration.</p>
                    </div>
                </div>
            </div>`;
    }

    if (frm.fields_dict.fcm_connection_status && frm.fields_dict.fcm_connection_status.$wrapper) {
        frm.fields_dict.fcm_connection_status.$wrapper.html(status_html);
    }
}
