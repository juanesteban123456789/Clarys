package com.clarys.app;

import android.text.InputType;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.widget.EditText;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Validacion de vistas en el dispositivo; no guarda productos ni altera la cuenta. */
@RunWith(AndroidJUnit4.class)
public class DeviceFormValidationTest {
    private String repeated(char character, int count) {
        char[] characters = new char[count];
        java.util.Arrays.fill(characters, character);
        return new String(characters);
    }
    @Test public void productFieldsErrorsNumericKeyboardAndCursor() {
        assumeTrue(BuildConfig.IS_ADMIN_APP && Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("clarysUiTest", "false")));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().adoptShellPermissionIdentity(
                "android.permission.START_ACTIVITIES_FROM_BACKGROUND");
        Log.i("ClarysFormTest", "Preparando formulario");
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                ProductFormActivity.class.getName(), null, false);
        Activity activity = null;
        try {
            instrumentation.runOnMainSync(() -> instrumentation.getTargetContext().startActivity(
                    new Intent(instrumentation.getTargetContext(), ProductFormActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            activity = monitor.waitForActivityWithTimeout(15000);
            Log.i("ClarysFormTest", "Formulario iniciado");
            assertNotNull("No se pudo abrir el formulario", activity);
            Activity form = activity;
            instrumentation.runOnMainSync(() -> {
                EditText name = form.findViewById(R.id.inputProductName);
                EditText description = form.findViewById(R.id.inputProductDescription);
                EditText category = form.findViewById(R.id.inputProductCategory);
                EditText salePrice = form.findViewById(R.id.inputProductSalePrice);
                form.findViewById(R.id.buttonSaveProductForm).performClick();
                Log.i("ClarysFormTest", "Campos vacios validados");
                assertEquals("El nombre es obligatorio", name.getError().toString());
                assertEquals("La categor\u00eda es obligatoria", category.getError().toString());
                assertNotNull(salePrice.getError());
                assertEquals("", description.getText().toString());
                String accentedName = "Camiseta ni\u00f1o - algod\u00f3n & <edici\u00f3n>";
                name.setText(accentedName);
                assertEquals(accentedName, name.getText().toString());
                name.setText(repeated('A', 81));
                description.setText(repeated('D', 501));
                form.findViewById(R.id.buttonSaveProductForm).performClick();
                Log.i("ClarysFormTest", "Longitudes validadas");
                if (name.length() > 80) {
                    assertEquals("El nombre es demasiado largo", name.getError().toString());
                } else { assertEquals(80, name.length()); }
                if (description.length() > 500) {
                    assertEquals("La descripci\u00f3n es demasiado larga", description.getError().toString());
                } else { assertEquals(500, description.length()); }
                for (int id : new int[]{R.id.inputProductPurchasePrice, R.id.inputProductSalePrice,
                        R.id.inputProductStock, R.id.inputProductMinStock}) {
                    EditText input = form.findViewById(id);
                    assertEquals(InputType.TYPE_CLASS_NUMBER, input.getInputType() & InputType.TYPE_MASK_CLASS);
                }
                for (int id : new int[]{R.id.inputProductPurchasePrice, R.id.inputProductSalePrice}) {
                    EditText money = form.findViewById(id);
                    money.setText("1234");
                    Log.i("ClarysFormTest", "Monto escrito: " + id);
                    assertEquals("1234", money.getText().toString().replaceAll("[^0-9]", ""));
                    money.setSelection(0);
                    Log.i("ClarysFormTest", "Cursor validado: " + id);
                    assertEquals(money.length(), money.getSelectionStart());
                }
            });
        } finally {
            if (activity != null) {
                Activity form = activity;
                instrumentation.runOnMainSync(form::finish);
            }
            instrumentation.removeMonitor(monitor);
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
