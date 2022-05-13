class Column:
    def __init__(self, name, alias=""):
        self.name = name
        self.alias = alias


class Partition:
    def __init__(self, number=0, by=""):
        self.number = number
        self.by = by


class Source:
    def __init__(self, format, options={}, columns=[], where="", printSchema=False, limit=-1, show=-1,
                 partition=Partition()):
        self.format = format
        self.options = options
        self.columns = columns
        self.where = where
        self.printSchema = printSchema
        self.limit = limit
        self.show = show
        self.partition = partition


class Target:
    def __init__(self, format, options, mode):
        self.format = format
        self.options = options
        self.mode = mode


class JobConfig:
    def __init__(self, name, conf, hadoopConfiguration, source, target, master=""):
        self.name = name
        self.conf = conf
        self.hadoopConfiguration = hadoopConfiguration
        self.source = source
        self.target = target
        self.master = master