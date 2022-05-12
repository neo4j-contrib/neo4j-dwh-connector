def _to_java_value(obj, sparkContext):
    if is_class(obj):
        obj = vars(obj)
    if type(obj) is dict:
        hashMap = sparkContext._jvm.java.util.HashMap()
        for key in obj:
            hashMap[key] = _to_java_value(obj[key], sparkContext)
        obj = hashMap
    if type(obj) is list:
        arrayList = sparkContext._jvm.java.util.ArrayList(len(obj))
        for key, val in enumerate(obj):
            arrayList.add(_to_java_value(obj[key], sparkContext))
        obj = arrayList
    return obj


# todo there should be a better way to do this
def is_class(obj):
    return ' object at ' in str(obj) and type(obj) is not list and type(obj) is not dict
