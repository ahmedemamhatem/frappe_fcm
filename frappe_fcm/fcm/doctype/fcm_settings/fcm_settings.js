// Copyright (c) 2025, Frappe FCM Contributors
// For license information, please see license.txt

frappe.ui.form.on("FCM Settings", {
    refresh: function(frm) {
        // Clear any existing buttons first
        frm.page.clear_primary_action();
        frm.page.clear_secondary_action();

        // Add primary button at top of page
        frm.page.set_primary_action(__("Test FCM Connection"), function() {
            test_fcm_connection(frm);
        }, "fa fa-check");

        // Update status display on refresh
        update_status_display(frm, null);
    }
});

function test_fcm_connection(frm) {
    frappe.call({
        method: "frappe_fcm.fcm.doctype.fcm_settings.fcm_settings.test_fcm_connection",
        freeze: true,
        freeze_message: __("Testing FCM connection..."),
        callback: function(r) {
            if (r.message) {
                show_connection_result(frm, r.message);
            } else {
                show_connection_result(frm, {
                    success: false,
                    message: __("No response from server")
                });
            }
        },
        error: function(r) {
            show_connection_result(frm, {
                success: false,
                message: __("Error testing connection. Please check your configuration.")
            });
        }
    });
}

function show_connection_result(frm, result) {
    // Show msgprint
    frappe.msgprint({
        title: result.success ? __("Connection Successful") : __("Connection Failed"),
        indicator: result.success ? "green" : "red",
        message: result.message +
            (result.project_id ? "<br><br><b>Project ID:</b> " + result.project_id : "") +
            (result.api_type ? "<br><b>API Type:</b> " + result.api_type : "")
    });

    // Update inline status
    update_status_display(frm, result);
}

function update_status_display(frm, result) {
    let status_html = "";

    if (result) {
        if (result.success) {
            status_html = `
                <div style="background: #d4edda; border: 1px solid #c3e6cb; border-radius: 8px; padding: 15px; margin-top: 10px;">
                    <div style="display: flex; align-items: center;">
                        <span style="font-size: 24px; margin-right: 10px;">&#10004;</span>
                        <div>
                            <strong style="color: #155724; font-size: 16px;">Connection Successful</strong>
                            <p style="margin: 5px 0 0 0; color: #155724;">${result.message}</p>
                            ${result.project_id ? `<p style="margin: 5px 0 0 0; color: #666;"><b>Project:</b> ${result.project_id}</p>` : ""}
                        </div>
                    </div>
                </div>`;
        } else {
            status_html = `
                <div style="background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 8px; padding: 15px; margin-top: 10px;">
                    <div style="display: flex; align-items: center;">
                        <span style="font-size: 24px; margin-right: 10px;">&#10008;</span>
                        <div>
                            <strong style="color: #721c24; font-size: 16px;">Connection Failed</strong>
                            <p style="margin: 5px 0 0 0; color: #721c24;">${result.message}</p>
                        </div>
                    </div>
                </div>`;
        }
    } else {
        status_html = `
            <div style="background: #e2e3e5; border: 1px solid #d6d8db; border-radius: 8px; padding: 15px; margin-top: 10px;">
                <div style="display: flex; align-items: center;">
                    <span style="font-size: 24px; margin-right: 10px;">&#9432;</span>
                    <div>
                        <strong style="color: #383d41; font-size: 16px;">Not Tested</strong>
                        <p style="margin: 5px 0 0 0; color: #383d41;">Click "Test FCM Connection" button above to verify your configuration.</p>
                    </div>
                </div>
            </div>`;
    }

    if (frm.fields_dict.fcm_connection_status) {
        frm.fields_dict.fcm_connection_status.$wrapper.html(status_html);
    }
}
