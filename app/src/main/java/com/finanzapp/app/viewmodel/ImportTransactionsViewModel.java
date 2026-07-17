package com.finanzapp.app.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.importer.CsvTransactionParser;
import com.finanzapp.app.data.importer.TransactionImportRepository;
import com.finanzapp.app.data.model.ImportResult;
import com.finanzapp.app.data.model.ImportedRow;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ImportTransactionsViewModel extends AndroidViewModel {
    private final TransactionImportRepository repository;
    private final CsvTransactionParser parser;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final SingleLiveEvent<ImportResult> importResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> error = new SingleLiveEvent<>();

    public ImportTransactionsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new TransactionImportRepository();
        this.parser = new CsvTransactionParser();
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<ImportResult> getImportResult() { return importResult; }
    public LiveData<String> getError() { return error; }

    public void importFromUri(String familyId, Uri uri) {
        loading.setValue(true);
        new Thread(() -> {
            ImportResult result = new ImportResult();
            try (InputStream is = getApplication().getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    postError("No se pudo abrir el archivo.");
                    return;
                }

                List<ImportedRow> rows = parser.parse(is, result);
                if (rows.isEmpty() && result.getErrors().isEmpty()) {
                    postError("No se encontraron movimientos válidos en el archivo.");
                    return;
                }

                if (!rows.isEmpty()) {
                    repository.importTransactions(familyId, rows, importRepoResult -> {
                        if (importRepoResult instanceof Result.Success) {
                            ImportResult finalResult = ((Result.Success<ImportResult>) importRepoResult).getData();
                            // Merge parsing errors if any
                            for (String parseError : result.getErrors()) {
                                finalResult.addError(parseError);
                            }
                            importResult.postValue(finalResult);
                        } else {
                            postError(((Result.Error<?>) importRepoResult).getException().getMessage());
                        }
                        loading.postValue(false);
                    });
                } else {
                    // Only parsing errors, no rows to import
                    importResult.postValue(result);
                    loading.postValue(false);
                }

            } catch (IOException e) {
                postError("Error al leer el archivo: " + e.getMessage());
                loading.postValue(false);
            }
        }).start();
    }

    private void postError(String message) {
        error.postValue(message);
        loading.postValue(false);
    }
}
