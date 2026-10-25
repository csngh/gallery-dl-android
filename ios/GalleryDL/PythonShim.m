#import "PythonShim.h"
#include <Python/Python.h>

static NSString *ErrorJSON(NSString *message) {
    NSData *data = [NSJSONSerialization dataWithJSONObject:@{@"error": message} options:0 error:nil];
    return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

/// Formats and clears the pending Python exception. Needs the GIL.
static NSString *TakeException(void) {
    PyObject *exc = PyErr_GetRaisedException();
    if (exc == NULL) return @"unknown Python error";
    NSString *text = nil;
    PyObject *traceback = PyImport_ImportModule("traceback");
    if (traceback != NULL) {
        PyObject *lines = PyObject_CallMethod(traceback, "format_exception", "O", exc);
        PyObject *empty = PyUnicode_FromString("");
        PyObject *joined = lines ? PyUnicode_Join(empty, lines) : NULL;
        Py_XDECREF(empty);
        const char *utf8 = joined ? PyUnicode_AsUTF8(joined) : NULL;
        if (utf8) text = [NSString stringWithUTF8String:utf8];
        Py_XDECREF(joined);
        Py_XDECREF(lines);
        Py_DECREF(traceback);
    }
    if (text == nil) {
        PyObject *str = PyObject_Str(exc);
        const char *utf8 = str ? PyUnicode_AsUTF8(str) : NULL;
        text = utf8 ? [NSString stringWithUTF8String:utf8] : @"unreadable Python error";
        Py_XDECREF(str);
    }
    PyErr_Clear();
    Py_DECREF(exc);
    return text;
}

static NSString *StatusMessage(PyStatus status, PyConfig *config) {
    NSString *message = status.err_msg ? [NSString stringWithUTF8String:status.err_msg] : @"unknown error";
    PyConfig_Clear(config);
    return message;
}

BOOL GDLStartPython(NSString *bundlePath, NSString **error) {
    PyStatus status;

    PyPreConfig preconfig;
    PyPreConfig_InitIsolatedConfig(&preconfig);
    preconfig.utf8_mode = 1;
    preconfig.configure_locale = 0;
    status = Py_PreInitialize(&preconfig);
    if (PyStatus_Exception(status)) {
        if (error) *error = [NSString stringWithFormat:@"Py_PreInitialize: %s", status.err_msg ?: "failed"];
        return NO;
    }

    PyConfig config;
    PyConfig_InitIsolatedConfig(&config);
    config.write_bytecode = 0;
    config.install_signal_handlers = 0;
    config.buffered_stdio = 0;
    config.module_search_paths_set = 1;

    NSString *home = [bundlePath stringByAppendingPathComponent:@"python"];
    status = PyConfig_SetBytesString(&config, &config.home, home.UTF8String);
    if (PyStatus_Exception(status)) {
        if (error) *error = StatusMessage(status, &config);
        return NO;
    }

    NSString *stdlib = [home stringByAppendingPathComponent:@"lib/python3.13"];
    NSArray<NSString *> *paths = @[
        [stdlib stringByAppendingString:@".zip"],
        stdlib,
        [stdlib stringByAppendingPathComponent:@"lib-dynload"],
        [bundlePath stringByAppendingPathComponent:@"app"],
        [bundlePath stringByAppendingPathComponent:@"app_packages"],
    ];
    for (NSString *path in paths) {
        wchar_t *wide = Py_DecodeLocale(path.UTF8String, NULL);
        status = PyWideStringList_Append(&config.module_search_paths, wide);
        PyMem_RawFree(wide);
        if (PyStatus_Exception(status)) {
            if (error) *error = StatusMessage(status, &config);
            return NO;
        }
    }

    status = Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) {
        if (error) *error = [NSString stringWithFormat:@"Py_InitializeFromConfig: %s", status.err_msg ?: "failed"];
        return NO;
    }

    // Initialisation leaves the GIL held by this thread; release it so GDLCall can take it from any thread.
    PyEval_SaveThread();
    return YES;
}

NSString *GDLCall(NSString *module, NSString *function, NSString *argument) {
    PyGILState_STATE gil = PyGILState_Ensure();
    NSString *result = nil;

    PyObject *mod = PyImport_ImportModule(module.UTF8String);
    PyObject *fn = mod ? PyObject_GetAttrString(mod, function.UTF8String) : NULL;
    PyObject *value = fn ? PyObject_CallFunction(fn, "s", argument.UTF8String) : NULL;

    if (value == NULL) {
        result = ErrorJSON(TakeException());
    } else {
        const char *utf8 = PyUnicode_AsUTF8(value);
        result = utf8 ? [NSString stringWithUTF8String:utf8] : ErrorJSON(TakeException());
    }

    Py_XDECREF(value);
    Py_XDECREF(fn);
    Py_XDECREF(mod);
    PyGILState_Release(gil);
    return result;
}
