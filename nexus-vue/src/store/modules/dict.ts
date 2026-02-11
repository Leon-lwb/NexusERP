interface DictItem {
  key: string
  value: any
}

interface DictState {
  dict: DictItem[]
}

const useDictStore = defineStore(
  'dict',
  {
    state: (): DictState => ({
      dict: []
    }),
    actions: {
      getDict(_key: string | null) {
        if (_key == null && _key == "") {
          return null
        }
        try {
          for (let i = 0; i < this.dict.length; i++) {
            if (this.dict[i].key == _key) {
              return this.dict[i].value
            }
          }
        } catch (e) {
          return null
        }
      },
      setDict(_key: string | null, value: any) {
        if (_key !== null && _key !== "") {
          this.dict.push({
            key: _key,
            value: value
          })
        }
      },
      removeDict(_key: string | null) {
        var bln = false
        try {
          for (let i = 0; i < this.dict.length; i++) {
            if (this.dict[i].key == _key) {
              this.dict.splice(i, 1)
              return true
            }
          }
        } catch (e) {
          bln = false
        }
        return bln
      },
      cleanDict() {
        this.dict = []
      },
      initDict() {
      }
    }
  })

export default useDictStore
