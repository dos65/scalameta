package lang.meta
package inputs

private[meta] trait Api {
}

private[meta] trait Aliases {
  type Input = lang.meta.inputs.Input
  object Input {
    val None = lang.meta.inputs.Input.None

    type String = lang.meta.inputs.Input.String
    val String = lang.meta.inputs.Input.String

    type Stream = lang.meta.inputs.Input.Stream
    val Stream = lang.meta.inputs.Input.Stream

    type File = lang.meta.inputs.Input.File
    val File = lang.meta.inputs.Input.File

    type VirtualFile = lang.meta.inputs.Input.VirtualFile
    val VirtualFile = lang.meta.inputs.Input.VirtualFile

    type Synthetic = lang.meta.inputs.Input.Synthetic
    val Synthetic = lang.meta.inputs.Input.Synthetic

    type Definition = lang.meta.inputs.Input.Definition
    val Definition = lang.meta.inputs.Input.Definition

    type Slice = lang.meta.inputs.Input.Slice
    val Slice = lang.meta.inputs.Input.Slice
  }

  type Position = lang.meta.inputs.Position
  object Position {
    val None = lang.meta.inputs.Position.None

    type Range = lang.meta.inputs.Position.Range
    val Range = lang.meta.inputs.Position.Range
  }
}
