#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Starts the embedded interpreter. Call once, before any GDLCall. Returns NO and fills `error` on failure.
BOOL GDLStartPython(NSString *bundlePath, NSString *_Nullable *_Nullable error);

/// Calls module.function(argument) with a single string and returns the string it returns.
/// Safe from any thread. A Python exception comes back as {"error": "..."}.
NSString *GDLCall(NSString *module, NSString *function, NSString *argument);

NS_ASSUME_NONNULL_END
